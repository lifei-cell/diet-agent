package com.diet.model;

import java.util.List;

import com.diet.enums.SourceMode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@NoArgsConstructor
public class MealResponse {
    private Long id;
    private SourceMode sourceType;
    private String name;
    private List<String> mealTime;
    private List<String> mood;
    private List<String> scene;
    private List<String> healthGoal;
    private List<String> cuisine;
    private List<String> taste;
    private List<String> convenience;
    private NutritionInfo nutrition;
    private double matchScore;

    public MealResponse(
            Long id,
            SourceMode sourceType,
            String name,
            List<String> mealTime,
            List<String> mood,
            List<String> scene,
            List<String> healthGoal,
            List<String> cuisine,
            List<String> taste,
            List<String> convenience,
            double matchScore
    ) {
        this(id, sourceType, name, mealTime, mood, scene, healthGoal, cuisine, taste, convenience, NutritionInfo.empty(), matchScore);
    }

    public MealResponse(
            Long id,
            SourceMode sourceType,
            String name,
            List<String> mealTime,
            List<String> mood,
            List<String> scene,
            List<String> healthGoal,
            List<String> cuisine,
            List<String> taste,
            List<String> convenience,
            NutritionInfo nutrition,
            double matchScore
    ) {
        this.id = id;
        this.sourceType = sourceType;
        this.name = name;
        this.mealTime = mealTime;
        this.mood = mood;
        this.scene = scene;
        this.healthGoal = healthGoal;
        this.cuisine = cuisine;
        this.taste = taste;
        this.convenience = convenience;
        this.nutrition = nutrition == null ? NutritionInfo.empty() : nutrition;
        this.matchScore = matchScore;
    }

    public static MealResponse from(MealItem item) {
        SlotBundle slots = item.slots();
        return new MealResponse(
                item.id(),
                item.sourceType(),
                item.name(),
                slots.mealTime(),
                slots.mood(),
                slots.scene(),
                slots.healthGoal(),
                slots.cuisine(),
                slots.taste(),
                slots.convenience(),
                item.nutrition(),
                item.matchScore()
        );
    }
}




