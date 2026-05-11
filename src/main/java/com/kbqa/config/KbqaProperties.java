package com.kbqa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kbqa")
public class KbqaProperties {

    private Document document = new Document();
    private Chunking chunking = new Chunking();
    private Retrieval retrieval = new Retrieval();
    private Prompt prompt = new Prompt();
    private Dedup dedup = new Dedup();
    private Embedding embedding = new Embedding();
    private Rerank rerank = new Rerank();

    @Data
    public static class Document {
        private String uploadDir = "./uploads";
        private String allowedTypes = "pdf,docx,doc,txt,md";
        private long maxSize = 52428800;
        private PdfParsing pdfParsing = new PdfParsing();
    }

    @Data
    public static class PdfParsing {
        private boolean detectImageOnly = true;
        private boolean stripHeadersFooters = true;
        private int headerFooterLines = 2;
    }

    @Data
    public static class Chunking {
        private int chunkSize = 500;
        private int chunkOverlap = 75;
        private boolean filterInvalidChunks = true;
        private int minChunkChars = 10;
        private double maxSpecialSymbolRatio = 0.5;
    }

    @Data
    public static class Retrieval {
        private int topK = 5;
        private double similarityThreshold = 0.5;
    }

    @Data
    public static class Prompt {
        private String systemTemplate = """
                你是一个知识库问答助手。请根据以下参考资料回答用户的问题。
                如果参考资料中没有相关信息，请明确说明"根据已有知识库无法回答该问题"，不要编造答案。
                回答时请引用来源文档名称。

                参考资料：
                {context}

                用户问题：{question}

                请给出准确、完整的回答：
                """;
    }

    @Data
    public static class Dedup {
        private boolean enabled = true;
        private boolean contentHash = true;
        private boolean semanticSimilarity = false;
        private double semanticThreshold = 0.95;
        private long contentHashTtlDays = 7;
    }

    @Data
    public static class Embedding {
        private int maxRetries = 3;
        private long retryIntervalMs = 1000;
        private double retryBackoffMultiplier = 2.0;
        private int timeoutSeconds = 30;
    }

    @Data
    public static class Rerank {
        private boolean enabled = false;
        private String model = "gte-rerank";
        private int topN = 3;
        private double scoreThreshold = 0.0;
    }
}
