package com.kbqa.parser;

import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.exception.ErrorCode;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc");
    }

    @Override
    public String parse(InputStream inputStream) throws DocumentProcessingException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            int paragraphCount = 0;
            int tableCount = 0;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                    paragraphCount++;
                }
            }

            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            sb.append(cellText).append(" ");
                        }
                    }
                    sb.append("\n");
                }
                tableCount++;
            }

            String content = sb.toString().trim();
            if (content.isEmpty()) {
                log.warn("[PARSE-WORD] Document content is empty after parsing");
            }
            log.debug("[PARSE-WORD] Parsed Word document: paragraphs={}, tables={}, length={}chars",
                    paragraphCount, tableCount, content.length());
            return content;
        } catch (Exception e) {
            log.error("[PARSE] Failed to parse Word document: error={}", e.getMessage(), e);
            throw new DocumentProcessingException(ErrorCode.DOC_PARSE_FAILED, "Word文档解析失败", e);
        }
    }
}
