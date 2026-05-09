package com.kbqa.service;

import com.kbqa.config.KbqaProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final KbqaProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    public List<Document> rerank(String query, List<Document> documents) {
        KbqaProperties.Rerank rerankCfg = properties.getRerank();

        if (!rerankCfg.isEnabled() || documents.isEmpty()) {
            return documents;
        }

        int topN = Math.min(rerankCfg.getTopN(), documents.size());
        log.debug("[RERANK] Starting rerank: query preview={}, docCount={}, topN={}, model={}",
                query.length() > 30 ? query.substring(0, 30) : query,
                documents.size(), topN, rerankCfg.getModel());

        try {
            List<Map<String, Object>> docTexts = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                docTexts.add(Map.of("text", documents.get(i).getText()));
            }

            Map<String, Object> requestBody = Map.of(
                    "model", rerankCfg.getModel(),
                    "query", query,
                    "documents", docTexts,
                    "top_n", topN,
                    "return_documents", false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-rerank/rerank",
                    entity, Map.class);

            if (response == null) {
                log.warn("[RERANK] API returned null, falling back to original order");
                return documents.subList(0, topN);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                log.warn("[RERANK] No results returned, falling back to original order");
                return documents.subList(0, topN);
            }

            List<Document> reranked = new ArrayList<>();
            for (Map<String, Object> result : results) {
                int index = ((Number) result.get("index")).intValue();
                double score = ((Number) result.getOrDefault("relevance_score", 0.0)).doubleValue();

                if (score < rerankCfg.getScoreThreshold()) {
                    log.debug("[RERANK] Skipping low-score doc: index={}, score={}", index, score);
                    continue;
                }

                Document doc = documents.get(index);
                doc.getMetadata().put("rerankScore", score);
                reranked.add(doc);
            }

            reranked.sort(Comparator.comparingDouble((Document d) ->
                    (double) d.getMetadata().getOrDefault("rerankScore", 0.0)).reversed());

            log.info("[RERANK] Completed: input={}, output={}", documents.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.error("[RERANK] Rerank failed, falling back to original results: error={}", e.getMessage(), e);
            return documents.subList(0, topN);
        }
    }
}
