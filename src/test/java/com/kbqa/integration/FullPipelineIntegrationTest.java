package com.kbqa.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import com.kbqa.model.DocumentInfo;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.service.ChatService;
import com.kbqa.service.DocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class FullPipelineIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ChatService chatService;

    @Test
    void fullPipeline_uploadAskAndVerify() {
        // Step 1: Upload document
        String content = """
                Spring AI知识库：
                Spring AI是Spring生态中的AI框架，提供对大语言模型的统一抽象。
                它支持多种模型提供商：OpenAI、Dashscope（通义千问）、Ollama等。
                核心组件包括ChatModel（对话模型）、EmbeddingModel（嵌入模型）、VectorStore（向量存储）。
                RAG（检索增强生成）是Spring AI的重要应用场景，流程为：文档分块→向量嵌入→相似检索→LLM生成。
                支持的向量数据库包括：Milvus、Chroma、Pinecone、Redis等。
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "spring-ai-kb.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        DocumentUploadResponse uploadResponse = documentService.uploadDocument(file);

        assertNotNull(uploadResponse.getDocId());
        assertEquals("spring-ai-kb.txt", uploadResponse.getFilename());
        assertTrue(uploadResponse.getChunkCount() > 0);

        // Step 2: List documents
        List<DocumentInfo> docs = documentService.listDocuments();
        assertFalse(docs.isEmpty());
        assertTrue(docs.stream().anyMatch(d -> d.getDocId().equals(uploadResponse.getDocId())));

        // Step 3: Ask question about the document
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setQuestion("Spring AI支持哪些向量数据库？");

        ChatResponse chatResponse = chatService.chat(chatRequest);

        assertNotNull(chatResponse.getAnswer());
        assertFalse(chatResponse.getAnswer().isBlank());
        assertNotNull(chatResponse.getSources());

        // Step 4: Delete document
        documentService.deleteDocument(uploadResponse.getDocId());

        List<DocumentInfo> docsAfterDelete = documentService.listDocuments();
        assertTrue(docsAfterDelete.stream()
                .noneMatch(d -> d.getDocId().equals(uploadResponse.getDocId())));
    }

    @Test
    void multipleDocumentsUploadAndQuery() {
        // Upload two documents
        String content1 = "Python基础：Python是一种解释型、面向对象的高级编程语言。";
        String content2 = "Go语言特性：Go是一种静态类型、编译型语言，由Google开发。";

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "python-intro.txt", "text/plain",
                content1.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "go-intro.txt", "text/plain",
                content2.getBytes(StandardCharsets.UTF_8));

        DocumentUploadResponse r1 = documentService.uploadDocument(file1);
        DocumentUploadResponse r2 = documentService.uploadDocument(file2);

        assertNotNull(r1.getDocId());
        assertNotNull(r2.getDocId());

        // Query
        ChatRequest request = new ChatRequest();
        request.setQuestion("Python是什么语言？");

        ChatResponse response = chatService.chat(request);

        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isBlank());

        // Cleanup
        documentService.deleteDocument(r1.getDocId());
        documentService.deleteDocument(r2.getDocId());
    }
}
