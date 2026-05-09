package com.kbqa.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kbqa.controller.DocumentController;
import com.kbqa.exception.GlobalExceptionHandler;
import com.kbqa.model.DocumentInfo;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.service.DocumentService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
@ContextConfiguration(classes = {DocumentController.class, GlobalExceptionHandler.class})
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    void uploadDocument_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());

        DocumentUploadResponse response = DocumentUploadResponse.builder()
                .docId("doc-123")
                .filename("test.txt")
                .chunkCount(1)
                .uploadTime("2026-01-01T00:00:00")
                .build();

        when(documentService.uploadDocument(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docId").value("doc-123"))
                .andExpect(jsonPath("$.filename").value("test.txt"))
                .andExpect(jsonPath("$.chunkCount").value(1));
    }

    @Test
    void listDocuments_empty() throws Exception {
        when(documentService.listDocuments()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listDocuments_withDocuments() throws Exception {
        DocumentInfo info = DocumentInfo.builder()
                .docId("doc-123")
                .filename("test.pdf")
                .uploadTime("2026-01-01T00:00:00")
                .chunkCount(5)
                .build();

        when(documentService.listDocuments()).thenReturn(List.of(info));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value("doc-123"))
                .andExpect(jsonPath("$[0].filename").value("test.pdf"));
    }

    @Test
    void deleteDocument_success() throws Exception {
        doNothing().when(documentService).deleteDocument("doc-123");

        mockMvc.perform(delete("/api/documents/doc-123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDocument_notFound() throws Exception {
        doThrow(new IllegalArgumentException("Document not found: nonexistent"))
                .when(documentService).deleteDocument("nonexistent");

        mockMvc.perform(delete("/api/documents/nonexistent"))
                .andExpect(status().isBadRequest());
    }
}
