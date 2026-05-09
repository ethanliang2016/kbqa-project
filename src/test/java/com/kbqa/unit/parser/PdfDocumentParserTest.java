package com.kbqa.unit.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kbqa.config.KbqaProperties;
import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.parser.PdfDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdfDocumentParserTest {

    private PdfDocumentParser parser;
    private KbqaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KbqaProperties();
        properties.getDocument().getPdfParsing().setDetectImageOnly(true);
        properties.getDocument().getPdfParsing().setStripHeadersFooters(false);
        parser = new PdfDocumentParser(properties);
    }

    @Test
    void supports_pdfFile() {
        assertTrue(parser.supports("test.pdf"));
        assertTrue(parser.supports("TEST.PDF"));
    }

    @Test
    void supports_unsupportedFile() {
        assertFalse(parser.supports("test.txt"));
        assertFalse(parser.supports("test.docx"));
    }

    @Test
    void supports_nullFilename() {
        assertFalse(parser.supports(null));
    }

    @Test
    void parse_simplePdf() throws Exception {
        byte[] pdfBytes = createSimplePdf("Hello PDF World");
        InputStream is = new ByteArrayInputStream(pdfBytes);

        String result = parser.parse(is);

        assertNotNull(result);
        assertTrue(result.contains("Hello PDF World"));
    }

    @Test
    void parse_multiPagePdf() throws Exception {
        byte[] pdfBytes = createMultiPagePdf();
        InputStream is = new ByteArrayInputStream(pdfBytes);

        String result = parser.parse(is);

        assertNotNull(result);
        assertTrue(result.contains("Page 1"));
        assertTrue(result.contains("Page 2"));
    }

    @Test
    void parse_emptyPdf_throwsImageOnlyException() throws Exception {
        byte[] pdfBytes = createEmptyPdf();
        InputStream is = new ByteArrayInputStream(pdfBytes);

        assertThrows(DocumentProcessingException.class, () -> parser.parse(is));
    }

    @Test
    void parse_emptyPdf_withDetectionDisabled() throws Exception {
        properties.getDocument().getPdfParsing().setDetectImageOnly(false);
        PdfDocumentParser parserNoDetect = new PdfDocumentParser(properties);

        byte[] pdfBytes = createEmptyPdf();
        InputStream is = new ByteArrayInputStream(pdfBytes);

        String result = parserNoDetect.parse(is);
        assertNotNull(result);
    }

    private byte[] createSimplePdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createMultiPagePdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            for (int i = 1; i <= 2; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(font, 12);
                    contentStream.newLineAtOffset(50, 700);
                    contentStream.showText("Page " + i + " content");
                    contentStream.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createEmptyPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
