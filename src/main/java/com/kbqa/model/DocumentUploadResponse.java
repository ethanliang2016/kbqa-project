package com.kbqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    private String docId;
    private String filename;
    private int chunkCount;
    private String uploadTime;
}
