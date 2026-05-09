package com.kbqa.exception;

import lombok.Getter;

@Getter
public class DocumentProcessingException extends RuntimeException {

    private final ErrorCode errorCode;

    public DocumentProcessingException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + (detail != null ? ": " + detail : ""));
        this.errorCode = errorCode;
    }

    public DocumentProcessingException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + (detail != null ? ": " + detail : ""), cause);
        this.errorCode = errorCode;
    }
}
