package com.kbqa.exception;

import lombok.Getter;

@Getter
public class DuplicateDocumentException extends RuntimeException {

    private final String existingFilename;

    public DuplicateDocumentException(String message, String existingFilename) {
        super(message);
        this.existingFilename = existingFilename;
    }

    public ErrorCode getErrorCode() {
        return ErrorCode.DOC_DUPLICATE;
    }
}
