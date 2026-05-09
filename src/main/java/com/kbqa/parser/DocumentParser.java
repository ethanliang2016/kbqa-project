package com.kbqa.parser;

import com.kbqa.exception.DocumentProcessingException;
import java.io.InputStream;

public interface DocumentParser {

    boolean supports(String filename);

    String parse(InputStream inputStream) throws DocumentProcessingException;
}
