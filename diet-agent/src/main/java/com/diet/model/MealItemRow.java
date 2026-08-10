package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MealItemRow {
    private Long id;
    private String sourceType;
    private Long ownerUserId;
    private String name;
    private String mealTime;
    private String mood;
    private String scene;
    private String healthGoal;
    private String cuisine;
    private String taste;
    private String convenience;
    private Double energyKcal;
    private Double proteinG;
    private Double fatG;
    private Double carbohydrateG;
    private Double fiberG;
    private Double sodiumMg;
    private String allergens;
    private String nutritionSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
