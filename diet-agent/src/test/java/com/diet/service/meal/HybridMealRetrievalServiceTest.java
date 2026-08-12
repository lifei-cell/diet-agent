package com.diet.service.meal;

import com.diet.enums.SourceMode;
import com.diet.model.MealItem;
import com.diet.model.MealSearchRequest;
import com.diet.model.NutritionConstraints;
import com.diet.model.SlotBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridMealRetrievalServiceTest {
    @Mock
    private MealCandidateRepository mealService;
    @Mock
    private KeywordMealCandidateRetriever keywordMealRetriever;
    @Mock
    private VectorMealCandidateRetriever vectorMealRecallService;

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseKeywordChannelForFoodEntityAndReturnFusedCandidate() {
        MealItem chicken = new MealItem(7L, SourceMode.PUBLIC, null, "鸡胸肉轻食碗", new SlotBundle(
                List.of("午餐"), List.of(), List.of(), List.of("高蛋白"), List.of("轻食"), List.of("清淡"), List.of()), 0);
        when(mealService.searchStructured(any(), any(), any(), any())).thenReturn(List.of());
        when(keywordMealRetriever.recall(any(), anyList())).thenReturn(List.of(chicken));
        when(vectorMealRecallService.recall(any())).thenReturn(List.of());

        HybridMealRetrievalService service = new HybridMealRetrievalService(
                mealService, new SlotQueryExpansionService(), keywordMealRetriever,
                vectorMealRecallService, new MealConstraintMatcher(), true, 80);
        List<MealItem> result = service.retrieve(new MealSearchRequest(
                SourceMode.PUBLIC, 1L,
                new SlotBundle(List.of("午餐"), List.of(), List.of(), List.of("高蛋白"), List.of(), List.of(), List.of()),
                NutritionConstraints.empty(), List.of(), "午餐来份鸡胸肉"));

        ArgumentCaptor<List<String>> keywords = ArgumentCaptor.forClass(List.class);
        verify(keywordMealRetriever).recall(any(), keywords.capture());
        assertTrue(keywords.getValue().contains("鸡胸肉"));
        assertEquals(List.of(7L), result.stream().map(MealItem::id).toList());
    }
}
