package com.kbqa.service.impl;

import com.kbqa.config.KbqaProperties;
import com.kbqa.exception.ChatException;
import com.kbqa.exception.ErrorCode;
import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import com.kbqa.model.ChatResponse.SourceReference;
import com.kbqa.service.ChatService;
import com.kbqa.service.RerankService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final KbqaProperties properties;
    private final RerankService rerankService;

    @Override
    public ChatResponse chat(ChatRequest request) {
        String question = request.getQuestion();
        log.info("[CHAT] Processing question: length={}chars, preview={}",
                question.length(), truncate(question, 50));

        long start = System.currentTimeMillis();
        List<Document> retrievedDocs = retrieveDocuments(question);
        long retrievalTime = System.currentTimeMillis() - start;

        // Rerank
        if (properties.getRerank().isEnabled() && !retrievedDocs.isEmpty()) {
            start = System.currentTimeMillis();
            retrievedDocs = rerankService.rerank(question, retrievedDocs);
            long rerankTime = System.currentTimeMillis() - start;
            log.info("[CHAT] Rerank completed: docs={}, time={}ms", retrievedDocs.size(), rerankTime);
        }

        String context = buildContext(retrievedDocs);

        start = System.currentTimeMillis();
        String answer = generateAnswer(question, context);
        long generationTime = System.currentTimeMillis() - start;

        List<SourceReference> sources = retrievedDocs.stream()
                .map(doc -> SourceReference.builder()
                        .filename((String) doc.getMetadata().getOrDefault("filename", "unknown"))
                        .similarity(doc.getMetadata().getOrDefault("distance", 0.0) instanceof Number n
                                ? 1.0 - n.doubleValue()
                                : 0.0)
                        .content(truncate(doc.getText(), 200))
                        .build())
                .toList();

        log.info("[CHAT] Completed: sources={}, retrievalTime={}ms, generationTime={}ms, answerLength={}chars",
                sources.size(), retrievalTime, generationTime, answer.length());

        return ChatResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    @Override
    public ChatResponse chatStream(ChatRequest request) {
        return chat(request);
    }

    private List<Document> retrieveDocuments(String question) {
        KbqaProperties.Retrieval retrieval = properties.getRetrieval();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(retrieval.getTopK())
                .similarityThreshold(retrieval.getSimilarityThreshold())
                .build();

        log.debug("[RETRIEVE] Searching Milvus: topK={}, threshold={}", retrieval.getTopK(), retrieval.getSimilarityThreshold());

        List<Document> results;
        try {
            results = vectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            log.error("[RETRIEVE] Milvus search failed: error={}", e.getMessage(), e);
            throw new ChatException(ErrorCode.CHAT_LLM_FAILED, "向量检索失败", e);
        }

        if (results.isEmpty()) {
            log.warn("[RETRIEVE] No relevant documents found for question: preview={}", truncate(question, 50));
        } else {
            log.info("[RETRIEVE] Retrieved {} document chunks", results.size());
            results.forEach(doc ->
                    log.debug("[RETRIEVE] Chunk hit: filename={}, similarity={}, preview={}",
                            doc.getMetadata().getOrDefault("filename", "unknown"),
                            doc.getMetadata().getOrDefault("distance", "N/A"),
                            truncate(doc.getText(), 60)));
        }

        return results;
    }

    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            log.debug("[CONTEXT] No documents, using fallback context");
            return "无相关参考资料";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
            sb.append("【来源").append(i + 1).append(": ").append(filename).append("】\n");
            sb.append(doc.getText()).append("\n\n");
        }
        log.debug("[CONTEXT] Built context from {} documents, contextLength={}chars", documents.size(), sb.length());
        return sb.toString();
    }

    private String generateAnswer(String question, String context) {
        String systemTemplate = properties.getPrompt().getSystemTemplate();
        String systemContent = systemTemplate
                .replace("{context}", context)
                .replace("{question}", question);

        SystemMessage systemMessage = new SystemMessage(systemContent);
        UserMessage userMessage = new UserMessage(question);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        try {
            log.debug("[GENERATE] Calling LLM: model={}", "qwen-plus");
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);
            String answer = aiResponse.getResult().getOutput().getText();
            if (answer == null || answer.isBlank()) {
                log.warn("[GENERATE] LLM returned empty response");
                throw new ChatException(ErrorCode.CHAT_LLM_FAILED, "大模型返回空响应");
            }
            return answer;
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GENERATE] LLM call failed: error={}", e.getMessage(), e);
            throw new ChatException(ErrorCode.CHAT_LLM_FAILED, "大模型调用失败", e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
