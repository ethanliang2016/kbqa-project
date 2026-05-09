package com.kbqa.unit.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kbqa.parser.WordDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WordDocumentParserTest {

    private WordDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new WordDocumentParser();
    }

    @Test
    void supports_docxFile() {
        assertTrue(parser.supports("test.docx"));
        assertTrue(parser.supports("TEST.DOCX"));
    }

    @Test
    void supports_docFile() {
        assertTrue(parser.supports("test.doc"));
    }

    @Test
    void supports_unsupportedFile() {
        assertFalse(parser.supports("test.pdf"));
        assertFalse(parser.supports("test.txt"));
    }

    @Test
    void supports_nullFilename() {
        assertFalse(parser.supports(null));
    }

    @Test
    void parse_simpleDocx() throws Exception {
        byte[] docxBytes = createSimpleDocx("Hello Word Document");
        InputStream is = new ByteArrayInputStream(docxBytes);

        String result = parser.parse(is);

        assertNotNull(result);
        assertTrue(result.contains("Hello Word Document"));
    }

    @Test
    void parse_docxWithTable() throws Exception {
        byte[] docxBytes = createDocxWithTable();
        InputStream is = new ByteArrayInputStream(docxBytes);

        String result = parser.parse(is);

        assertNotNull(result);
        assertTrue(result.contains("Header1"));
        assertTrue(result.contains("Header2"));
        assertTrue(result.contains("Cell1"));
        assertTrue(result.contains("Cell2"));
    }

    @Test
    void parse_emptyDocx() throws Exception {
        byte[] docxBytes = createEmptyDocx();
        InputStream is = new ByteArrayInputStream(docxBytes);

        String result = parser.parse(is);

        assertNotNull(result);
    }

    private byte[] createSimpleDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createDocxWithTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Document with table");

            XWPFTable table = document.createTable();
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Header1");
            headerRow.addNewTableCell().setText("Header2");

            XWPFTableRow dataRow = table.createRow();
            dataRow.getCell(0).setText("Cell1");
            dataRow.getCell(1).setText("Cell2");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createEmptyDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            return baos.toByteArray();
        }
    }
}
