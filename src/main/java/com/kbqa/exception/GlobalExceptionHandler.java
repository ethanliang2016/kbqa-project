package com.kbqa.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDocument(
            DuplicateDocumentException e, HttpServletRequest request) {
        log.warn("[EXCEPTION] Duplicate document: existingFile={}, message={}", e.getExistingFilename(), e.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .code(e.getErrorCode().getCode())
                .message(e.getMessage())
                .path(request.getRequestURI())
                .detail(e.getExistingFilename())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ErrorResponse> handleDocumentProcessing(
            DocumentProcessingException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = resolveHttpStatus(errorCode);
        log.error("[EXCEPTION] Document processing error: code={}, message={}", errorCode.getCode(), e.getMessage(), e);
        ErrorResponse response = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(e.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorResponse> handleChatException(
            ChatException e, HttpServletRequest request) {
        log.error("[EXCEPTION] Chat error: code={}, message={}", e.getErrorCode().getCode(), e.getMessage(), e);
        ErrorResponse response = ErrorResponse.builder()
                .code(e.getErrorCode().getCode())
                .message(e.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[EXCEPTION] Invalid request: {}", e.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message(e.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("[EXCEPTION] File too large: {}", e.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.DOC_FILE_TOO_LARGE.getCode())
                .message(ErrorCode.DOC_FILE_TOO_LARGE.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[EXCEPTION] Missing parameter: {}", e.getParameterName());
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message("缺少请求参数: " + e.getParameterName())
                .path(request.getRequestURI())
                .detail("参数类型: " + e.getParameterType())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException e, HttpServletRequest request) {
        log.warn("[EXCEPTION] Missing request part: {}", e.getRequestPartName());
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message("缺少请求部分: " + e.getRequestPartName())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("[EXCEPTION] Validation error: {}", detail);
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message(ErrorCode.INVALID_REQUEST.getMessage())
                .path(request.getRequestURI())
                .detail(detail)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[EXCEPTION] Resource not found: {}", request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message("请求路径不存在: " + request.getRequestURI())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception e, HttpServletRequest request) {
        log.error("[EXCEPTION] Unexpected error: {}", e.getMessage(), e);
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ErrorCode.INTERNAL_ERROR.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case DOC_EMPTY, DOC_UNSUPPORTED_TYPE, DOC_FILE_TOO_LARGE, DOC_FILENAME_EMPTY ->
                    HttpStatus.BAD_REQUEST;
            case DOC_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DOC_DUPLICATE -> HttpStatus.CONFLICT;
            case DOC_PARSE_FAILED, DOC_READ_FAILED, DOC_DELETE_FAILED ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
