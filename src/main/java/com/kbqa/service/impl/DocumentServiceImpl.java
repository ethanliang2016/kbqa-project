package com.kbqa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kbqa.config.KbqaProperties;
import com.kbqa.config.KbqaProperties.Dedup;
import com.kbqa.entity.DocumentChunkEntity;
import com.kbqa.entity.DocumentEntity;
import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.exception.DuplicateDocumentException;
import com.kbqa.exception.ErrorCode;
import com.kbqa.mapper.DocumentChunkMapper;
import com.kbqa.mapper.DocumentMapper;
import com.kbqa.model.DocumentInfo;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.parser.DocumentParser;
import com.kbqa.service.DocumentService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String REDIS_KEY_PREFIX = "kbqa:dedup:hash:";

    private final VectorStore vectorStore;
    private final List<DocumentParser> parsers;
    private final KbqaProperties properties;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final StringRedisTemplate redisTemplate;

    public DocumentServiceImpl(@Lazy VectorStore vectorStore,
                               List<DocumentParser> parsers,
                               KbqaProperties properties,
                               DocumentMapper documentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               StringRedisTemplate redisTemplate) {
        this.vectorStore = vectorStore;
        this.parsers = parsers;
        this.properties = properties;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("[UPLOAD] Start uploading document: filename={}, size={}KB", filename, fileSize / 1024);

        validateFile(file, filename);

        long start = System.currentTimeMillis();
        String content = parseContent(file, filename);
        if (content == null || content.isBlank()) {
            throw new DocumentProcessingException(ErrorCode.DOC_EMPTY, filename);
        }
        log.info("[UPLOAD] Document parsed: filename={}, contentLength={}chars, elapsed={}ms",
                filename, content.length(), System.currentTimeMillis() - start);

        Dedup dedup = properties.getDedup();
        String contentHash = sha256(content);
        log.debug("[UPLOAD] Content hash computed: filename={}, hash={}", filename, contentHash);

        if (dedup.isEnabled() && dedup.isContentHash()) {
            checkContentHashDuplicate(contentHash, filename);
            log.info("[UPLOAD] Content hash dedup passed: filename={}", filename);
        }

        String docId = UUID.randomUUID().toString();
        String uploadTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        List<Document> chunks = splitIntoChunks(content, filename, docId, uploadTime, contentHash);
        log.info("[UPLOAD] Document split into chunks: docId={}, chunkCount={}", docId, chunks.size());

        List<Document> chunksToStore = chunks;
        if (dedup.isEnabled() && dedup.isSemanticSimilarity()) {
            start = System.currentTimeMillis();
            chunksToStore = filterSemanticDuplicates(chunks, dedup.getSemanticThreshold());
            log.info("[UPLOAD] Semantic dedup completed: total={}, kept={}, skipped={}, elapsed={}ms",
                    chunks.size(), chunksToStore.size(), chunks.size() - chunksToStore.size(),
                    System.currentTimeMillis() - start);
        }

        start = System.currentTimeMillis();
        try {
            storeWithRetry(chunksToStore);
        } catch (Exception e) {
            log.error("[UPLOAD] Failed to store chunks to Milvus after retries: docId={}, chunkCount={}, error={}",
                    docId, chunksToStore.size(), e.getMessage(), e);
            throw new DocumentProcessingException(ErrorCode.CHAT_EMBEDDING_FAILED, "向量库写入失败", e);
        }
        log.info("[UPLOAD] Chunks stored to Milvus: count={}, elapsed={}ms",
                chunksToStore.size(), System.currentTimeMillis() - start);

        // Save document metadata to MySQL
        DocumentEntity docEntity = DocumentEntity.builder()
                .docId(docId)
                .filename(filename)
                .uploadTime(LocalDateTime.parse(uploadTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .chunkCount(chunksToStore.size())
                .contentHash(contentHash)
                .build();
        documentMapper.insert(docEntity);

        // Save chunk IDs to MySQL
        List<String> chunkIdList = chunksToStore.stream().map(Document::getId).toList();
        for (String chunkId : chunkIdList) {
            documentChunkMapper.insert(DocumentChunkEntity.builder()
                    .docId(docId)
                    .chunkId(chunkId)
                    .build());
        }

        // Save content hash to Redis with TTL
        if (contentHash != null) {
            long ttlDays = properties.getDedup().getContentHashTtlDays();
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + contentHash, docId, ttlDays, TimeUnit.DAYS);
        }

        saveOriginalFile(file, docId, filename);

        log.info("[UPLOAD] Document uploaded successfully: docId={}, filename={}, chunks={}, contentHash={}",
                docId, filename, chunksToStore.size(), contentHash);

        return DocumentUploadResponse.builder()
                .docId(docId)
                .filename(filename)
                .chunkCount(chunksToStore.size())
                .uploadTime(uploadTime)
                .build();
    }

    @Override
    public List<DocumentInfo> listDocuments() {
        List<DocumentEntity> entities = documentMapper.selectList(null);
        List<DocumentInfo> docs = entities.stream()
                .map(this::toDocumentInfo)
                .toList();
        log.debug("[LIST] Returning {} documents", docs.size());
        return docs;
    }

    @Override
    @Transactional
    public void deleteDocument(String docId) {
        log.info("[DELETE] Deleting document: docId={}", docId);

        DocumentEntity docEntity = documentMapper.selectOne(
                new LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getDocId, docId));
        if (docEntity == null) {
            throw new DocumentProcessingException(ErrorCode.DOC_NOT_FOUND, docId);
        }

        // Clean up Redis content-hash index
        if (docEntity.getContentHash() != null) {
            redisTemplate.delete(REDIS_KEY_PREFIX + docEntity.getContentHash());
            log.debug("[DELETE] Content hash index removed from Redis: hash={}", docEntity.getContentHash());
        }

        // Get chunk IDs for Milvus deletion
        List<DocumentChunkEntity> chunkEntities = documentChunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunkEntity>().eq(DocumentChunkEntity::getDocId, docId));
        List<String> chunkIds = chunkEntities.stream()
                .map(DocumentChunkEntity::getChunkId)
                .toList();

        if (!chunkIds.isEmpty()) {
            try {
                long start = System.currentTimeMillis();
                vectorStore.delete(chunkIds);
                log.info("[DELETE] Document deleted from Milvus: docId={}, filename={}, chunks={}, elapsed={}ms",
                        docId, docEntity.getFilename(), chunkIds.size(), System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("[DELETE] Failed to delete chunks from Milvus: docId={}, filename={}, chunkCount={}",
                        docId, docEntity.getFilename(), chunkIds.size(), e);
                throw new DocumentProcessingException(ErrorCode.DOC_DELETE_FAILED, docEntity.getFilename(), e);
            }
        } else {
            log.warn("[DELETE] No chunk IDs found for document: docId={}, filename={}", docId, docEntity.getFilename());
        }

        documentChunkMapper.delete(
                new LambdaQueryWrapper<DocumentChunkEntity>().eq(DocumentChunkEntity::getDocId, docId));
        documentMapper.delete(
                new LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getDocId, docId));

        log.info("[DELETE] Document deleted from MySQL: docId={}, filename={}", docId, docEntity.getFilename());
    }

    private void checkContentHashDuplicate(String contentHash, String filename) {
        String redisKey = REDIS_KEY_PREFIX + contentHash;

        // Level 1: Check Redis (fast path)
        try {
            String existingDocId = redisTemplate.opsForValue().get(redisKey);
            if (existingDocId != null) {
                DocumentEntity existing = documentMapper.selectOne(
                        new LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getDocId, existingDocId));
                String existingName = existing != null ? existing.getFilename() : existingDocId;
                log.warn("[DEDUP-HASH] Duplicate detected via Redis: currentFile={}, existingFile={}, existingDocId={}",
                        filename, existingName, existingDocId);
                throw new DuplicateDocumentException(
                        "文档内容重复，已存在相同内容的文档: " + existingName, existingName);
            }
        } catch (DuplicateDocumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[DEDUP-HASH] Redis check failed, falling back to MySQL: filename={}, error={}",
                    filename, e.getMessage());
        }

        // Level 2: Check MySQL (reliable fallback)
        try {
            Long count = documentMapper.selectCount(
                    new LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getContentHash, contentHash));
            if (count != null && count > 0) {
                DocumentEntity existing = documentMapper.selectOne(
                        new LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getContentHash, contentHash));
                String existingName = existing != null ? existing.getFilename() : "unknown";
                log.warn("[DEDUP-HASH] Duplicate detected via MySQL: currentFile={}, existingFile={}",
                        filename, existingName);
                throw new DuplicateDocumentException(
                        "文档内容重复，已存在相同内容的文档: " + existingName, existingName);
            }
        } catch (DuplicateDocumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[DEDUP-HASH] MySQL check failed, skipping dedup: filename={}, error={}",
                    filename, e.getMessage());
        }

        log.debug("[DEDUP-HASH] No duplicate found: filename={}, hash={}", filename, contentHash);
    }

    private List<Document> filterSemanticDuplicates(List<Document> chunks, double threshold) {
        List<Document> uniqueChunks = new ArrayList<>();
        int skipped = 0;

        for (Document chunk : chunks) {
            try {
                List<Document> similar = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(chunk.getText())
                                .topK(1)
                                .similarityThreshold(threshold)
                                .build());

                if (similar.isEmpty()) {
                    uniqueChunks.add(chunk);
                } else {
                    skipped++;
                    log.debug("[DEDUP-SEMANTIC] Skipped duplicate chunk: threshold={}, preview={}",
                            threshold, truncate(chunk.getText(), 80));
                }
            } catch (Exception e) {
                log.warn("[DEDUP-SEMANTIC] Check failed, keeping chunk as fallback: error={}", e.getMessage());
                uniqueChunks.add(chunk);
            }
        }

        if (skipped > 0) {
            log.info("[DEDUP-SEMANTIC] Result: total={}, kept={}, skipped={}", chunks.size(), uniqueChunks.size(), skipped);
        }
        return uniqueChunks;
    }

    private void validateFile(MultipartFile file, String filename) {
        if (filename == null || filename.isBlank()) {
            throw new DocumentProcessingException(ErrorCode.DOC_FILENAME_EMPTY, null);
        }

        String extension = getFileExtension(filename);
        String allowedTypes = properties.getDocument().getAllowedTypes();
        if (!allowedTypes.contains(extension)) {
            log.warn("[VALIDATE] Unsupported file type: filename={}, extension={}, allowed={}",
                    filename, extension, allowedTypes);
            throw new DocumentProcessingException(ErrorCode.DOC_UNSUPPORTED_TYPE,
                    filename + " (允许类型: " + allowedTypes + ")");
        }

        if (file.getSize() > properties.getDocument().getMaxSize()) {
            log.warn("[VALIDATE] File too large: filename={}, size={}KB, max={}KB",
                    filename, file.getSize() / 1024, properties.getDocument().getMaxSize() / 1024);
            throw new DocumentProcessingException(ErrorCode.DOC_FILE_TOO_LARGE,
                    filename + " (" + file.getSize() / 1024 + "KB, 上限: " + properties.getDocument().getMaxSize() / 1024 + "KB)");
        }

        log.debug("[VALIDATE] File validation passed: filename={}, extension={}, size={}KB",
                filename, extension, file.getSize() / 1024);
    }

    private String parseContent(MultipartFile file, String filename) {
        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(filename))
                .findFirst()
                .orElseThrow(() -> new DocumentProcessingException(ErrorCode.DOC_UNSUPPORTED_TYPE, filename));

        log.debug("[PARSE] Using parser: filename={}, parser={}", filename, parser.getClass().getSimpleName());

        try {
            return parser.parse(file.getInputStream());
        } catch (IOException e) {
            log.error("[PARSE] Failed to read file: filename={}", filename, e);
            throw new DocumentProcessingException(ErrorCode.DOC_READ_FAILED, filename, e);
        }
    }

    private List<Document> splitIntoChunks(String content, String filename,
                                           String docId, String uploadTime, String contentHash) {
        Map<String, Object> metadata = Map.of(
                "docId", docId,
                "filename", filename,
                "uploadTime", uploadTime,
                "contentHash", contentHash
        );

        Document doc = new Document(content, metadata);

        int chunkSize = properties.getChunking().getChunkSize();
        int chunkOverlap = properties.getChunking().getChunkOverlap();
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 100, true);

        List<Document> chunks = splitter.apply(List.of(doc));
        log.debug("[SPLIT] Split complete: docId={}, rawChunks={}, chunkSize={}, overlap={}",
                docId, chunks.size(), chunkSize, chunkOverlap);

        if (properties.getChunking().isFilterInvalidChunks()) {
            chunks = filterInvalidChunks(chunks);
        }

        return chunks;
    }

    private List<Document> filterInvalidChunks(List<Document> chunks) {
        int minChars = properties.getChunking().getMinChunkChars();
        double maxSymbolRatio = properties.getChunking().getMaxSpecialSymbolRatio();
        List<Document> valid = new ArrayList<>();
        int filtered = 0;

        for (Document chunk : chunks) {
            String text = chunk.getText();
            if (text == null || text.isBlank()) {
                filtered++;
                continue;
            }
            if (text.length() < minChars) {
                filtered++;
                log.debug("[FILTER] Chunk too short: length={}, preview={}", text.length(), truncate(text, 40));
                continue;
            }
            long specialCount = text.chars()
                    .filter(c -> !Character.isLetterOrDigit(c) && !Character.isIdeographic(c)
                            && !Character.isWhitespace(c))
                    .count();
            double symbolRatio = (double) specialCount / text.length();
            if (symbolRatio > maxSymbolRatio) {
                filtered++;
                log.debug("[FILTER] Chunk too many symbols: ratio={}, preview={}", String.format("%.2f", symbolRatio), truncate(text, 40));
                continue;
            }
            valid.add(chunk);
        }

        if (filtered > 0) {
            log.info("[FILTER] Filtered invalid chunks: total={}, kept={}, removed={}", chunks.size(), valid.size(), filtered);
        }
        return valid;
    }

    private void saveOriginalFile(MultipartFile file, String docId, String filename) {
        try {
            Path uploadDir = Paths.get(properties.getDocument().getUploadDir()).toAbsolutePath();
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(docId + "_" + filename);
            file.transferTo(filePath);
            log.info("[SAVE] Original file saved: filename={}, path={}, size={}KB",
                    filename, filePath, file.getSize() / 1024);
        } catch (IOException e) {
            log.error("[SAVE] Failed to save original file: filename={}, error={}", filename, e.getMessage(), e);
        }
    }

    private DocumentInfo toDocumentInfo(DocumentEntity entity) {
        return DocumentInfo.builder()
                .docId(entity.getDocId())
                .filename(entity.getFilename())
                .uploadTime(entity.getUploadTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .chunkCount(entity.getChunkCount())
                .contentHash(entity.getContentHash())
                .build();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new DocumentProcessingException(ErrorCode.INTERNAL_ERROR, "SHA-256算法不可用", e);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private void storeWithRetry(List<Document> chunks) {
        KbqaProperties.Embedding embCfg = properties.getEmbedding();
        int maxRetries = embCfg.getMaxRetries();
        long intervalMs = embCfg.getRetryIntervalMs();
        double backoff = embCfg.getRetryBackoffMultiplier();

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                vectorStore.add(chunks);
                log.debug("[EMBEDDING] Stored {} chunks successfully on attempt {}", chunks.size(), attempt);
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt <= maxRetries) {
                    long waitMs = (long) (intervalMs * Math.pow(backoff, attempt - 1));
                    log.warn("[EMBEDDING] Store attempt {}/{} failed, retrying in {}ms: error={}",
                            attempt, maxRetries + 1, waitMs, e.getMessage());
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DocumentProcessingException(ErrorCode.CHAT_EMBEDDING_FAILED, "向量存储重试被中断", ie);
                    }
                } else {
                    log.error("[EMBEDDING] All {} attempts exhausted for storing {} chunks",
                            maxRetries + 1, chunks.size());
                }
            }
        }
        throw new DocumentProcessingException(ErrorCode.CHAT_EMBEDDING_FAILED, "向量存储失败，已重试" + maxRetries + "次", lastException);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
