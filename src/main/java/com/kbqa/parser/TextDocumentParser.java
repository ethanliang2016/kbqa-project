package com.kbqa.parser;

import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }

    @Override
    public String parse(InputStream inputStream) throws DocumentProcessingException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String filename = "unknown";

            List<String> lines = reader.lines().toList();
            boolean isMarkdown = false;
            List<String> processed = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("#") && trimmed.matches("^#{1,6}\\s+.*")) {
                    isMarkdown = true;
                    processed.add("");
                    processed.add(trimmed);
                    processed.add("");
                } else {
                    processed.add(line);
                }
            }

            String content = processed.stream().collect(Collectors.joining("\n")).trim();

            if (content.isEmpty()) {
                log.warn("[PARSE-TEXT] Document content is empty after parsing");
            }

            log.debug("[PARSE-TEXT] Parsed {} document: length={}chars{}, isMarkdown={}",
                    isMarkdown ? "Markdown" : "text",
                    content.length(),
                    isMarkdown ? " (structure preserved)" : "",
                    isMarkdown);
            return content;
        } catch (Exception e) {
            log.error("[PARSE] Failed to parse text document: error={}", e.getMessage(), e);
            throw new DocumentProcessingException(ErrorCode.DOC_PARSE_FAILED, "文本文件解析失败", e);
        }
    }
}
