package com.kbqa.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kbqa.config.KbqaProperties;
import com.kbqa.exception.ChatException;
import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import com.kbqa.service.RerankService;
import com.kbqa.service.impl.ChatServiceImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private RerankService rerankService;

    private ChatServiceImpl service;
    private KbqaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KbqaProperties();
        properties.getRetrieval().setTopK(5);
        properties.getRetrieval().setSimilarityThreshold(0.5);

        service = new ChatServiceImpl(vectorStore, chatModel, properties, rerankService);
    }

    @Test
    void chat_withRetrievedDocs() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filename", "java-interview.pdf");
        metadata.put("distance", 0.2);

        Document doc = new Document("Java is a popular programming language.", metadata);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("Java是一种流行的编程语言。");
        org.springframework.ai.chat.model.Generation generation =
                new org.springframework.ai.chat.model.Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse aiChatResponse =
                new org.springframework.ai.chat.model.ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(aiChatResponse);

        ChatRequest request = new ChatRequest();
        request.setQuestion("什么是Java？");

        ChatResponse response = service.chat(request);

        assertNotNull(response.getAnswer());
        assertFalse(response.getSources().isEmpty());
        assertEquals("java-interview.pdf", response.getSources().get(0).getFilename());
    }

    @Test
    void chat_noRelevantDocs() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(new ArrayList<>());

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage(
                        "根据已有知识库无法回答该问题");
        org.springframework.ai.chat.model.Generation generation =
                new org.springframework.ai.chat.model.Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse aiChatResponse =
                new org.springframework.ai.chat.model.ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(aiChatResponse);

        ChatRequest request = new ChatRequest();
        request.setQuestion("什么是量子计算？");

        ChatResponse response = service.chat(request);

        assertNotNull(response.getAnswer());
        assertTrue(response.getSources().isEmpty());
    }

    @Test
    void chat_multipleSources() {
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("filename", "doc1.pdf");
        meta1.put("distance", 0.15);

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("filename", "doc2.pdf");
        meta2.put("distance", 0.25);

        Document doc1 = new Document("Content from doc1", meta1);
        Document doc2 = new Document("Content from doc2", meta2);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("综合回答");
        org.springframework.ai.chat.model.Generation generation =
                new org.springframework.ai.chat.model.Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse aiChatResponse =
                new org.springframework.ai.chat.model.ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(aiChatResponse);

        ChatRequest request = new ChatRequest();
        request.setQuestion("测试问题");

        ChatResponse response = service.chat(request);

        assertEquals(2, response.getSources().size());
    }

    @Test
    void chat_llmError() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(new ArrayList<>());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API error"));

        ChatRequest request = new ChatRequest();
        request.setQuestion("测试问题");

        assertThrows(ChatException.class, () -> service.chat(request));
    }
}
