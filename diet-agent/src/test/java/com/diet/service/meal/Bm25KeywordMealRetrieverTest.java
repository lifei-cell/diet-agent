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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Bm25KeywordMealRetrieverTest {
    @Mock
    private MealCandidateRepository mealRepository;

    @Test
    void shouldRankSpecificDishNameAheadOfGenericTagMatch() {
        MealItem chicken = meal(7L, "香煎鸡胸肉轻食碗", List.of("高蛋白", "减脂"));
        MealItem beef = meal(8L, "黑椒牛肉轻食碗", List.of("高蛋白", "减脂"));
        when(mealRepository.searchKeywordCorpus(any(), any(), any(), anyInt())).thenReturn(List.of(beef, chicken));

        List<MealItem> result = retriever().recall(request(), List.of("高蛋白", "鸡胸肉"));

        assertEquals(List.of(7L, 8L), result.stream().map(MealItem::id).toList());
    }

    @Test
    void shouldRecallKeywordFromStructuredTagAndUseBoundedCorpus() {
        MealItem lowFat = meal(7L, "鸡胸肉沙拉", List.of("低脂"));
        MealItem noodle = meal(8L, "番茄鸡蛋面", List.of("高碳水"));
        when(mealRepository.searchKeywordCorpus(any(), any(), any(), anyInt())).thenReturn(List.of(noodle, lowFat));

        List<MealItem> result = retriever().recall(request(), List.of("低脂"));

        assertEquals(List.of(7L), result.stream().map(MealItem::id).toList());
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(mealRepository).searchKeywordCorpus(any(), any(), any(), limit.capture());
        assertEquals(500, limit.getValue());
    }

    private Bm25KeywordMealRetriever retriever() {
        return new Bm25KeywordMealRetriever(mealRepository, 500, 50, 1.2, 0.75);
    }

    private MealSearchRequest request() {
        return new MealSearchRequest(SourceMode.PUBLIC, 1L, SlotBundle.empty(), NutritionConstraints.empty(), List.of(), "想吃鸡胸肉");
    }

    private MealItem meal(Long id, String name, List<String> healthGoal) {
        return new MealItem(id, SourceMode.PUBLIC, null, name,
                new SlotBundle(List.of("午餐"), List.of(), List.of(), healthGoal, List.of(), List.of(), List.of()), 0);
    }
}
