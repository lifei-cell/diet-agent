package com.diet.service.meal;

import com.diet.enums.SourceMode;
import com.diet.mapper.FeedbackMapper;
import com.diet.mapper.UserPreferenceMapper;
import com.diet.model.MealFeedbackScoreRow;
import com.diet.model.MealItem;
import com.diet.model.MealRankRequest;
import com.diet.model.MealRankResult;
import com.diet.model.NutritionConstraints;
import com.diet.model.NutritionInfo;
import com.diet.model.SlotBundle;
import com.diet.model.UserSlotPreferenceRow;
import com.diet.service.preference.UserPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealRankServiceTest {

    @Mock
    private UserPreferenceMapper userPreferenceMapper;

    @Mock
    private FeedbackMapper feedbackMapper;

    private MealRankService mealRankService;

    @BeforeEach
    void setUp() {
        UserPreferenceService userPreferenceService = new UserPreferenceService(userPreferenceMapper, feedbackMapper);
        mealRankService = new MealRankService(userPreferenceService);
    }

    @Test
    void shouldPromoteMealMatchingLongTermSlotPreference() {
        UserSlotPreferenceRow preference = new UserSlotPreferenceRow();
        preference.setSlotName("taste");
        preference.setOptionValue("清淡");
        preference.setPreferenceScore(8.0);
        when(userPreferenceMapper.findSlotPreferences(1L)).thenReturn(List.of(preference));
        when(feedbackMapper.findMealFeedbackScores(ArgumentMatchers.eq(1L), ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        MealRankResult result = mealRankService.rank(new MealRankRequest(
                List.of(spicyMeal(), lightMeal()), querySlots(), 1L, NutritionConstraints.empty(), List.of()));

        assertEquals(2L, result.meals().getFirst().id());
        assertTrue(result.scores().getFirst().preferenceScore() > result.scores().get(1).preferenceScore());
        assertEquals(1.0, result.scores().getFirst().contextScore());
    }

    @Test
    void shouldPromoteMealWithPositiveHistoricalFeedback() {
        MealFeedbackScoreRow feedbackScore = new MealFeedbackScoreRow();
        feedbackScore.setItemId(1L);
        feedbackScore.setFeedbackScore(6.0);
        when(userPreferenceMapper.findSlotPreferences(1L)).thenReturn(List.of());
        when(feedbackMapper.findMealFeedbackScores(ArgumentMatchers.eq(1L), ArgumentMatchers.anyList()))
                .thenReturn(List.of(feedbackScore));

        MealRankResult result = mealRankService.rank(new MealRankRequest(
                List.of(spicyMeal(), lightMeal()), querySlots(), 1L, NutritionConstraints.empty(), List.of()));

        assertEquals(1L, result.meals().getFirst().id());
        assertTrue(result.scores().getFirst().feedbackScore() > result.scores().get(1).feedbackScore());
    }

    @Test
    void shouldPromoteMealWithMoreComfortableNutritionMarginAfterHardFiltering() {
        NutritionConstraints constraints = new NutritionConstraints(600.0, 30.0, null, null, null, List.of());
        MealItem nearEnergyLimit = nutritionMeal(1L, "接近热量上限餐", 580.0, 30.0);
        MealItem lowerEnergyMeal = nutritionMeal(2L, "低热量高蛋白餐", 350.0, 30.0);
        when(userPreferenceMapper.findSlotPreferences(1L)).thenReturn(List.of());
        when(feedbackMapper.findMealFeedbackScores(ArgumentMatchers.eq(1L), ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        MealRankResult result = mealRankService.rank(new MealRankRequest(
                List.of(nearEnergyLimit, lowerEnergyMeal), querySlots(), 1L, constraints, List.of()));

        assertEquals(2L, result.meals().getFirst().id());
        assertTrue(result.scores().getFirst().nutritionScore() > result.scores().get(1).nutritionScore());
    }

    private MealItem spicyMeal() {
        return new MealItem(1L, SourceMode.PUBLIC, null, "香辣鸡胸肉", new SlotBundle(
                List.of("午餐"), List.of(), List.of(), List.of("高蛋白"), List.of(), List.of("辣"), List.of()), 0);
    }

    private MealItem lightMeal() {
        return new MealItem(2L, SourceMode.PUBLIC, null, "清淡鸡胸肉", new SlotBundle(
                List.of("午餐"), List.of(), List.of(), List.of("高蛋白"), List.of(), List.of("清淡"), List.of()), 0);
    }

    private SlotBundle querySlots() {
        return new SlotBundle(List.of("午餐"), List.of(), List.of(), List.of("高蛋白"), List.of(), List.of(), List.of());
    }

    private MealItem nutritionMeal(Long id, String name, Double energyKcal, Double proteinG) {
        return new MealItem(
                id,
                SourceMode.PUBLIC,
                null,
                name,
                querySlots(),
                new NutritionInfo(energyKcal, proteinG, 8.0, 35.0, 6.0, 500.0, List.of(), "TEST"),
                0
        );
    }
}
