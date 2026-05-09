package com.kbqa.service;

import com.kbqa.model.DocumentInfo;
import com.kbqa.model.DocumentUploadResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    DocumentUploadResponse uploadDocument(MultipartFile file);

    List<DocumentInfo> listDocuments();

    void deleteDocument(String docId);
}
