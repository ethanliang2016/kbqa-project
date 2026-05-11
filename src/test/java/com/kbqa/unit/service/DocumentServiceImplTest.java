package com.kbqa.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kbqa.config.KbqaProperties;
import com.kbqa.entity.DocumentChunkEntity;
import com.kbqa.entity.DocumentEntity;
import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.mapper.DocumentChunkMapper;
import com.kbqa.mapper.DocumentMapper;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.parser.DocumentParser;
import com.kbqa.service.impl.DocumentServiceImpl;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentParser textParser;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

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

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new DocumentServiceImpl(vectorStore, List.of(textParser), properties,
                documentMapper, documentChunkMapper, redisTemplate);
    }

    @Test
    void uploadDocument_success() throws IOException {
        String content = "This is a test document with enough content for testing the upload flow.";
        when(textParser.parse(any())).thenReturn(content);
        doNothing().when(vectorStore).add(any());
        when(documentMapper.insert(any(DocumentEntity.class))).thenReturn(1);
        when(documentChunkMapper.insert(any(DocumentChunkEntity.class))).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain",
                content.getBytes());

        DocumentUploadResponse response = service.uploadDocument(file);

        assertNotNull(response.getDocId());
        assertEquals("test.txt", response.getFilename());
        assertEquals(1, response.getChunkCount());
        verify(vectorStore).add(any());
        verify(documentMapper).insert(any(DocumentEntity.class));
        verify(valueOperations).set(anyString(), anyString(), anyLong(), eq(TimeUnit.DAYS));
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

        DocumentServiceImpl svc = new DocumentServiceImpl(vectorStore, List.of(textParser), props,
                documentMapper, documentChunkMapper, redisTemplate);

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
        when(documentMapper.selectList(any())).thenReturn(List.of());
        List<com.kbqa.model.DocumentInfo> docs = service.listDocuments();
        assertNotNull(docs);
        assertEquals(0, docs.size());
    }

    @Test
    void deleteDocument_notFound() {
        when(documentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThrows(DocumentProcessingException.class, () -> service.deleteDocument("nonexistent-id"));
    }

    @Test
    void deleteDocument_success() throws IOException {
        String content = "Document content for deletion test.";
        when(textParser.parse(any())).thenReturn(content);
        doNothing().when(vectorStore).add(any());
        doNothing().when(vectorStore).delete(anyList());
        when(documentMapper.insert(any(DocumentEntity.class))).thenReturn(1);
        when(documentChunkMapper.insert(any(DocumentChunkEntity.class))).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", content.getBytes());

        DocumentUploadResponse response = service.uploadDocument(file);

        // Setup mocks for deletion
        DocumentEntity docEntity = DocumentEntity.builder()
                .id(1L)
                .docId(response.getDocId())
                .filename("test.txt")
                .uploadTime(LocalDateTime.now())
                .chunkCount(1)
                .contentHash("testhash")
                .build();
        when(documentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(docEntity);

        DocumentChunkEntity chunkEntity = DocumentChunkEntity.builder()
                .id(1L)
                .docId(response.getDocId())
                .chunkId("chunk-1")
                .build();
        when(documentChunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(chunkEntity));
        when(documentChunkMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(documentMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        service.deleteDocument(response.getDocId());

        verify(vectorStore).delete(anyList());
        verify(documentChunkMapper).delete(any(LambdaQueryWrapper.class));
        verify(documentMapper).delete(any(LambdaQueryWrapper.class));
    }
}
