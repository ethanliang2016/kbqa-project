package com.kbqa.exception;

import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {

    private final ErrorCode errorCode;

    public ChatException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + (detail != null ? ": " + detail : ""));
        this.errorCode = errorCode;
    }

    public ChatException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + (detail != null ? ": " + detail : ""), cause);
        this.errorCode = errorCode;
    }
}
