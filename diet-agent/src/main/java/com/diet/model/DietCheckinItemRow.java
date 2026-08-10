package com.diet.model;

import lombok.Data;

@Data
public class DietCheckinItemRow {
    private Long id;
    private Long checkinId;
    private String foodName;
    private Double estimatedWeightG;
    private Double energyKcal;
    private Double proteinG;
    private Double fatG;
    private Double carbohydrateG;
    private Double confidence;
    private String nutritionSource;
}
