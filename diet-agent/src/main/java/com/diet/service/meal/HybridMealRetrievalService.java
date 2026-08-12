package com.diet.service.meal;

import com.diet.model.MealItem;
import com.diet.model.MealSearchRequest;
import com.diet.model.SlotBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 三路混合召回：结构化标签、BM25 关键词、可选向量服务。各通道只负责扩大候选，
 * RRF 融合后仍由 MealRankService 做个性化重排。
 */
@Service
public class HybridMealRetrievalService {
    private static final int RRF_K = 60;

    private final MealCandidateRepository mealService;
    private final SlotQueryExpansionService expansionService;
    private final KeywordMealCandidateRetriever keywordMealRetriever;
    private final VectorMealCandidateRetriever vectorMealRecallService;
    private final MealConstraintMatcher constraintMatcher;
    private final boolean keywordEnabled;
    private final int candidateLimit;

    public HybridMealRetrievalService(
            MealCandidateRepository mealService,
            SlotQueryExpansionService expansionService,
            KeywordMealCandidateRetriever keywordMealRetriever,
            VectorMealCandidateRetriever vectorMealRecallService,
            MealConstraintMatcher constraintMatcher,
            @Value("${diet.retrieval.keyword.enabled:true}") boolean keywordEnabled,
            @Value("${diet.retrieval.candidate-limit:80}") int candidateLimit
    ) {
        this.mealService = mealService;
        this.expansionService = expansionService;
        this.keywordMealRetriever = keywordMealRetriever;
        this.vectorMealRecallService = vectorMealRecallService;
        this.constraintMatcher = constraintMatcher;
        this.keywordEnabled = keywordEnabled;
        this.candidateLimit = Math.max(10, Math.min(200, candidateLimit));
    }

    public List<MealItem> retrieve(MealSearchRequest request) {
        SlotBundle originalSlots = request.slots() == null ? SlotBundle.empty() : request.slots();
        SlotBundle expandedSlots = expansionService.expand(originalSlots);

        // 结构化通道维持现有主能力，并用受控同义词提高标签召回率。
        List<MealItem> structured = sortBySlotCoverage(
                mealService.searchStructured(request.sourceMode(), request.userId(), expandedSlots, request.nutritionConstraints()),
                expandedSlots
        );

        // BM25 词法通道覆盖菜名和标签文本，补足“鸡胸肉/盖饭/馄饨”等实体表达；硬约束仍由主库过滤。
        List<String> keywords = keywordTerms(request.queryText(), originalSlots);
        List<MealItem> keyword = keywordEnabled && !keywords.isEmpty()
                ? keywordMealRetriever.recall(request, keywords)
                : List.of();

        // 外部服务不可信：只接收 id，再从本库按 sourceMode/userId 取回并二次过滤硬约束。
        List<MealItem> vector = vectorCandidates(request);

        return reciprocalRankFuse(structured, keyword, vector);
    }

    private List<MealItem> vectorCandidates(MealSearchRequest request) {
        List<Long> ids = vectorMealRecallService.recall(request);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, MealItem> byId = new LinkedHashMap<>();
        for (MealItem meal : mealService.findAccessibleByIds(request.sourceMode(), request.userId(), ids)) {
            if (constraintMatcher.matches(meal, request.nutritionConstraints())) {
                byId.put(meal.id(), meal);
            }
        }
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** RRF 只使用各通道名次，避免不同后端分数的量纲不可比较。 */
    private List<MealItem> reciprocalRankFuse(List<MealItem> structured, List<MealItem> keyword, List<MealItem> vector) {
        Map<Long, FusedMeal> fused = new LinkedHashMap<>();
        addRanks(fused, structured);
        addRanks(fused, keyword);
        addRanks(fused, vector);
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(FusedMeal::score).reversed()
                        .thenComparing(item -> item.meal().id()))
                .limit(candidateLimit)
                .map(item -> new MealItem(
                        item.meal().id(), item.meal().sourceType(), item.meal().ownerUserId(), item.meal().name(),
                        item.meal().slots(), item.meal().nutrition(), item.score()))
                .toList();
    }

    private void addRanks(Map<Long, FusedMeal> target, List<MealItem> meals) {
        for (int index = 0; index < meals.size(); index++) {
            MealItem meal = meals.get(index);
            if (meal == null || meal.id() == null) {
                continue;
            }
            FusedMeal current = target.get(meal.id());
            double score = 1.0 / (RRF_K + index + 1.0);
            target.put(meal.id(), current == null ? new FusedMeal(meal, score) : new FusedMeal(current.meal(), current.score() + score));
        }
    }

    private List<MealItem> sortBySlotCoverage(List<MealItem> meals, SlotBundle query) {
        return meals.stream()
                .sorted(Comparator.<MealItem>comparingDouble(meal -> slotCoverage(meal.slots(), query)).reversed()
                        .thenComparingLong(meal -> meal.id() == null ? Long.MAX_VALUE : meal.id()))
                .toList();
    }

    private double slotCoverage(SlotBundle item, SlotBundle query) {
        List<List<String>> itemDimensions = dimensions(item);
        List<List<String>> queryDimensions = dimensions(query);
        double total = 0;
        int active = 0;
        for (int i = 0; i < queryDimensions.size(); i++) {
            List<String> values = queryDimensions.get(i);
            if (values.isEmpty()) {
                continue;
            }
            active++;
            Set<String> itemValues = Set.copyOf(itemDimensions.get(i));
            total += values.stream().filter(itemValues::contains).count() / (double) values.size();
        }
        return active == 0 ? 0 : total / active;
    }

    /**
     * 从原话提取 2-6 位中文片段，并合并结构化标签。无需中文分词依赖，适合当前小中型菜品库；
     * 作为 BM25 查询词；菜品规模增大后可将关键词检索器替换为 OpenSearch，融合层无需变更。
     */
    private List<String> keywordTerms(String queryText, SlotBundle slots) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(expansionService.expandedValues(slots));
        String compact = queryText == null ? "" : queryText.replaceAll("[^\\u4e00-\\u9fffA-Za-z0-9]", "");
        if (compact.length() >= 2) {
            for (int length = Math.min(6, compact.length()); length >= 2 && terms.size() < 24; length--) {
                for (int start = 0; start + length <= compact.length() && terms.size() < 24; start++) {
                    String term = compact.substring(start, start + length);
                    if (!isFiller(term)) {
                        terms.add(term);
                    }
                }
            }
        }
        return terms.stream().filter(term -> term.length() >= 2).limit(24).toList();
    }

    private boolean isFiller(String value) {
        return List.of("我想", "想吃", "一份", "一个", "什么", "推荐", "今天", "晚上", "中午", "早餐", "午餐", "晚餐", "可以", "不要")
                .contains(value);
    }

    private List<List<String>> dimensions(SlotBundle slots) {
        SlotBundle safe = slots == null ? SlotBundle.empty() : slots;
        return List.of(safe.mealTime(), safe.mood(), safe.scene(), safe.healthGoal(), safe.cuisine(), safe.taste(), safe.convenience());
    }

    private record FusedMeal(MealItem meal, double score) {
    }
}
