package com.kbqa.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kbqa.config.KbqaProperties;
import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.parser.DocumentParser;
import com.kbqa.service.impl.DocumentServiceImpl;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentParser textParser;

    private DocumentServiceImpl service;
    private KbqaProperties properties;

    @TempDir
    java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new KbqaProperties();
        properties.getDocument().setUploadDir(tempDir.toString());
        properties.getDocument().setAllowedTypes("pdf,docx,doc,txt,md");
        properties.getDocument().setMaxSize(52428800);
        properties.getChunking().setChunkSize(800);
        properties.getChunking().setChunkOverlap(200);
        properties.getChunking().setFilterInvalidChunks(false);

        when(textParser.supports(any(String.class))).thenAnswer(inv -> {
            String filename = inv.getArgument(0);
            return filename != null && filename.toLowerCase().endsWith(".txt");
        });

        service = new DocumentServiceImpl(vectorStore, List.of(textParser), properties);
    }

    @Test
    void uploadDocument_success() throws IOException {
        String content = "This is a test document with enough content for testing the upload flow.";
        when(textParser.parse(any())).thenReturn(content);
        doNothing().when(vectorStore).add(any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain",
                content.getBytes());

        DocumentUploadResponse response = service.uploadDocument(file);

        assertNotNull(response.getDocId());
        assertEquals("test.txt", response.getFilename());
        assertEquals(1, response.getChunkCount());
        verify(vectorStore).add(any());
    }

    @Test
    void uploadDocument_unsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xyz", "application/octet-stream",
                "content".getBytes());

        assertThrows(DocumentProcessingException.class, () -> service.uploadDocument(file));
    }

    @Test
    void uploadDocument_emptyContent() throws IOException {
        when(textParser.parse(any())).thenReturn("");

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain",
                "".getBytes());

        assertThrows(DocumentProcessingException.class, () -> service.uploadDocument(file));
    }

    @Test
    void uploadDocument_fileTooLarge() {
        KbqaProperties props = new KbqaProperties();
        props.getDocument().setUploadDir(tempDir.toString());
        props.getDocument().setAllowedTypes("pdf,docx,doc,txt,md");
        props.getDocument().setMaxSize(10);
        props.getChunking().setChunkSize(800);
        props.getChunking().setChunkOverlap(200);

        DocumentServiceImpl svc = new DocumentServiceImpl(vectorStore, List.of(textParser), props);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain",
                "This content is longer than 10 bytes".getBytes());

        assertThrows(DocumentProcessingException.class, () -> svc.uploadDocument(file));
    }

    @Test
    void uploadDocument_nullFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/plain", "content".getBytes());

        assertThrows(DocumentProcessingException.class, () -> service.uploadDocument(file));
    }

    @Test
    void listDocuments_empty() {
        List<com.kbqa.model.DocumentInfo> docs = service.listDocuments();
        assertNotNull(docs);
        assertEquals(0, docs.size());
    }

    @Test
    void deleteDocument_notFound() {
        assertThrows(DocumentProcessingException.class, () -> service.deleteDocument("nonexistent-id"));
    }

    @Test
    void deleteDocument_success() throws IOException {
        String content = "Document content for deletion test.";
        when(textParser.parse(any())).thenReturn(content);
        doNothing().when(vectorStore).add(any());
        doNothing().when(vectorStore).delete(anyList());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", content.getBytes());

        DocumentUploadResponse response = service.uploadDocument(file);

        assertEquals(1, service.listDocuments().size());

        service.deleteDocument(response.getDocId());

        assertEquals(0, service.listDocuments().size());
        verify(vectorStore).delete(anyList());
    }
}
