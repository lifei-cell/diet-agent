package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

/** Persistent lifecycle record for one client-generated chat request id. */
@Data
public class ChatIdempotencyRow {
    private Long id;
    private Long userId;
    private String sessionId;
    private String requestId;
    private String requestHash;
    private String status;
    private String processingToken;
    private String responseJson;
    private String traceId;
    private String failureCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
