package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CheckinDraftRow {
    private String id;
    private Long userId;
    private String imageObjectKey;
    private String imageMediaType;
    private String recognizedItems;
    private Boolean automated;
    private String message;
    private LocalDateTime createdAt;
}
