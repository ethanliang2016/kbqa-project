package com.kbqa.parser;

import com.kbqa.config.KbqaProperties;
import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.exception.ErrorCode;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfDocumentParser implements DocumentParser {

    private static final int IMAGE_ONLY_MIN_CHARS = 5;

    private final KbqaProperties properties;

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public String parse(InputStream inputStream) throws DocumentProcessingException {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                KbqaProperties.PdfParsing pdfCfg = properties.getDocument().getPdfParsing();
                int totalPages = document.getNumberOfPages();

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);

                int imageOnlyPages = 0;
                int blankPages = 0;
                StringBuilder content = new StringBuilder();

                for (int i = 1; i <= totalPages; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document);

                    if (pageText == null || pageText.isBlank()) {
                        blankPages++;
                        log.debug("[PARSE-PDF] Skipping blank page: {}/{}", i, totalPages);
                        continue;
                    }

                    if (pdfCfg.isDetectImageOnly() && pageText.trim().length() < IMAGE_ONLY_MIN_CHARS) {
                        imageOnlyPages++;
                        log.debug("[PARSE-PDF] Skipping image-only page: {}/{}", i, totalPages);
                        continue;
                    }

                    if (pdfCfg.isStripHeadersFooters()) {
                        pageText = stripHeadersFooters(pageText, pdfCfg.getHeaderFooterLines());
                    }

                    content.append(pageText);
                }

                int textPages = totalPages - blankPages - imageOnlyPages;

                if (pdfCfg.isDetectImageOnly() && imageOnlyPages > 0) {
                    log.warn("[PARSE-PDF] Detected {} image-only pages out of {}", imageOnlyPages, totalPages);
                }

                if (textPages == 0 && pdfCfg.isDetectImageOnly()) {
                    log.error("[PARSE-PDF] All {} pages are image-only or blank, OCR required", totalPages);
                    throw new DocumentProcessingException(ErrorCode.DOC_IMAGE_ONLY,
                            "PDF全部为图片，共" + totalPages + "页，需要OCR识别");
                }

                String result = content.toString().trim();
                log.info("[PARSE-PDF] Parsed PDF: totalPages={}, textPages={}, imagePages={}, blankPages={}, contentLength={}chars",
                        totalPages, textPages, imageOnlyPages, blankPages, result.length());
                return result;
            }
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PARSE] Failed to parse PDF document: error={}", e.getMessage(), e);
            throw new DocumentProcessingException(ErrorCode.DOC_PARSE_FAILED, "PDF解析失败", e);
        }
    }

    private String stripHeadersFooters(String pageText, int linesToRemove) {
        if (linesToRemove <= 0) {
            return pageText;
        }
        String[] lines = pageText.split("\n");
        if (lines.length <= linesToRemove * 2) {
            return pageText;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = linesToRemove; i < lines.length - linesToRemove; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }
}
