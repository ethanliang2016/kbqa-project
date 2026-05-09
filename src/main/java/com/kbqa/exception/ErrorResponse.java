package com.kbqa.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int code;
    private String message;
    private String path;
    @Builder.Default
    private long timestamp = Instant.now().toEpochMilli();
    private String detail;
}
