package com.kbqa.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String answer;
    private List<SourceReference> sources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceReference {
        private String filename;
        private double similarity;
        private String content;
    }
}
