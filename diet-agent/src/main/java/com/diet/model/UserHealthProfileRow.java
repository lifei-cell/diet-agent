package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserHealthProfileRow {
    private Long userId;
    private Double heightCm;
    private Double weightKg;
    private Integer age;
    private String activityLevel;
    private String diseaseHistory;
    private String profileGoal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
