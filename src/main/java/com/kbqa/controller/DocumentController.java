package com.kbqa.controller;

import com.kbqa.model.DocumentInfo;
import com.kbqa.model.DocumentUploadResponse;
import com.kbqa.service.DocumentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        log.info("[API] Upload request received: filename={}, size={}KB",
                file.getOriginalFilename(), file.getSize() / 1024);
        DocumentUploadResponse response = documentService.uploadDocument(file);
        log.info("[API] Upload response: docId={}, chunks={}", response.getDocId(), response.getChunkCount());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<DocumentInfo>> listDocuments() {
        List<DocumentInfo> documents = documentService.listDocuments();
        log.debug("[API] List documents: count={}", documents.size());
        return ResponseEntity.ok(documents);
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String docId) {
        log.info("[API] Delete request received: docId={}", docId);
        documentService.deleteDocument(docId);
        log.info("[API] Delete completed: docId={}", docId);
        return ResponseEntity.noContent().build();
    }
}
