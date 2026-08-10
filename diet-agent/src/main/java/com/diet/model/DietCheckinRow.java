package com.diet.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DietCheckinRow {
    private Long id;
    private Long userId;
    private LocalDate checkinDate;
    private String mealTime;
    private String imageObjectKey;
    private String imageMediaType;
    private Double totalEnergyKcal;
    private Double totalProteinG;
    private Double totalFatG;
    private Double totalCarbohydrateG;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
