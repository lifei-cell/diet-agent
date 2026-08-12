package com.diet.model;

import com.diet.enums.SourceMode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MealItem {
    private Long id;
    private SourceMode sourceType;
    private Long ownerUserId;
    private String name;
    private SlotBundle slots;
    private NutritionInfo nutrition;
    private double matchScore;

    public MealItem(Long id, SourceMode sourceType, Long ownerUserId, String name, SlotBundle slots, double matchScore) {
        this(id, sourceType, ownerUserId, name, slots, NutritionInfo.empty(), matchScore);
    }

    public MealItem(
            Long id,
            SourceMode sourceType,
            Long ownerUserId,
            String name,
            SlotBundle slots,
            NutritionInfo nutrition,
            double matchScore
    ) {
        this.id = id;
        this.sourceType = sourceType;
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.slots = slots == null ? SlotBundle.empty() : slots;
        this.nutrition = nutrition == null ? NutritionInfo.empty() : nutrition;
        this.matchScore = matchScore;
    }

    public double matchScore() {
        return matchScore;
    }
}




