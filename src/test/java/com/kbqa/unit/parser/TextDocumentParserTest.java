package com.kbqa.unit.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kbqa.exception.DocumentProcessingException;
import com.kbqa.parser.TextDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TextDocumentParserTest {

    private TextDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new TextDocumentParser();
    }

    @Test
    void supports_txtFile() {
        assertTrue(parser.supports("test.txt"));
        assertTrue(parser.supports("TEST.TXT"));
    }

    @Test
    void supports_mdFile() {
        assertTrue(parser.supports("readme.md"));
        assertTrue(parser.supports("README.MD"));
    }

    @Test
    void supports_unsupportedFile() {
        assertFalse(parser.supports("test.pdf"));
        assertFalse(parser.supports("test.docx"));
    }

    @Test
    void supports_nullFilename() {
        assertFalse(parser.supports(null));
    }

    @Test
    void parse_simpleText() {
        String text = "Hello World\nThis is a test document.";
        InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(is);

        assertEquals(text, result);
    }

    @Test
    void parse_chineseText() {
        String text = "这是一个中文测试文档\n第二行内容";
        InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(is);

        assertEquals(text, result);
    }

    @Test
    void parse_emptyStream() {
        InputStream is = new ByteArrayInputStream(new byte[0]);

        String result = parser.parse(is);

        assertEquals("", result);
    }
}
