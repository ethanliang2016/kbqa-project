package com.kbqa.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kbqa.model.DocumentInfo;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.service.DocumentService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class DocumentIngestionIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Test
    void uploadTextDocument_toMilvus() {
        String content = "Spring AI is a framework for building AI applications with Spring Boot. "
                + "It provides abstractions for chat models, embedding models, and vector stores. "
                + "The framework integrates with multiple providers including OpenAI, Dashscope, and Ollama.";

        MockMultipartFile file = new MockMultipartFile(
                "file", "spring-ai-intro.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        DocumentUploadResponse response = documentService.uploadDocument(file);

        assertNotNull(response.getDocId());
        assertEquals("spring-ai-intro.txt", response.getFilename());
        assertNotNull(response.getChunkCount());
        assertFalse(response.getChunkCount() <= 0);
    }

    @Test
    void uploadPdfDocument_toMilvus() throws Exception {
        byte[] pdfBytes = createTestPdf(
                "Java Interview Questions and Answers. "
                        + "Q1: What is JVM? A1: JVM stands for Java Virtual Machine. "
                        + "It is the runtime engine that executes Java bytecode. "
                        + "Q2: What is JDK? A2: JDK stands for Java Development Kit.");

        MockMultipartFile file = new MockMultipartFile(
                "file", "java-interview.pdf", "application/pdf", pdfBytes);

        DocumentUploadResponse response = documentService.uploadDocument(file);

        assertNotNull(response.getDocId());
        assertEquals("java-interview.pdf", response.getFilename());
    }

    @Test
    void listDocuments_afterUpload() {
        String content = "Test document for listing integration test.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "list-test.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        documentService.uploadDocument(file);

        List<DocumentInfo> docs = documentService.listDocuments();

        assertNotNull(docs);
        assertFalse(docs.isEmpty());
    }

    @Test
    void deleteDocument_afterUpload() {
        String content = "Test document for deletion integration test.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "delete-test.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        DocumentUploadResponse response = documentService.uploadDocument(file);

        documentService.deleteDocument(response.getDocId());

        List<DocumentInfo> docs = documentService.listDocuments();
        boolean exists = docs.stream()
                .anyMatch(d -> d.getDocId().equals(response.getDocId()));
        assertFalse(exists);
    }

    private byte[] createTestPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 11);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
