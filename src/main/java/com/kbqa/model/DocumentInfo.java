package com.kbqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {

    private String docId;
    private String filename;
    private String uploadTime;
    private int chunkCount;
    private String contentHash;
}
