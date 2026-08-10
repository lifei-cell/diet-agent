package com.diet.service.meal;

import com.diet.model.MealItem;
import com.diet.model.MealRankRequest;
import com.diet.model.MealRankResult;
import com.diet.model.MealRankScore;
import com.diet.model.SlotBundle;
import com.diet.service.preference.UserPreferenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 餐食重排服务（Orchestrator 推荐流水线第二层）。
 * 消费 slots、excludeMealIds，对检索候选二次打分排序。
 */
@Service
public class MealRankService {

    private static final double CONTEXT_WEIGHT = 0.70;
    private static final double PREFERENCE_WEIGHT = 0.20;
    private static final double FEEDBACK_WEIGHT = 0.10;
    private static final double NEUTRAL_SCORE = 0.50;
    private static final double MAX_RAW_PERSONAL_SCORE = 10.0;

    private final UserPreferenceService userPreferenceService;

    public MealRankService(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    /**
     * 在search检索时,只要用户输入的槽位信息有匹配有交集就会返回
     * 但是rank排序,是比较用户输入的槽位信息是否更大程度的得到满足
     * 执行重排并返回最多 10 个候选。
     * 由 Orchestrator#completeRecommendation 在 MEAL_SEARCHED 之后调用。
     */
    public MealRankResult rank(MealRankRequest request) {
        // 将 excludeMealIds 转为 HashSet，便于 O(1) 查找
        Set<Long> excludeIds = new HashSet<>(request.excludeMealIds() == null ? List.of() : request.excludeMealIds());
        List<MealItem> candidates = request.candidates() == null ? List.of() : request.candidates().stream()
                .filter(item -> item != null && !excludeIds.contains(item.id()))
                .toList();
        if (candidates.isEmpty()) {
            return new MealRankResult(List.of(), List.of());
        }

        Map<String, Double> slotPreferenceScores = userPreferenceService.slotPreferenceScores(request.userId());
        Map<Long, Double> mealFeedbackScores = userPreferenceService.mealFeedbackScores(
                request.userId(), candidates.stream().map(MealItem::id).filter(java.util.Objects::nonNull).toList());
        List<ScoredMeal> scoredMeals = candidates.stream()
                .map(item -> score(item, request.slots(), slotPreferenceScores, mealFeedbackScores))
                .sorted(Comparator.comparingDouble((ScoredMeal item) -> item.score().finalScore()).reversed())
                .limit(10)
                .toList();

        List<MealItem> ranked = new ArrayList<>();
        List<MealRankScore> details = new ArrayList<>();
        for (ScoredMeal scored : scoredMeals) {
            ranked.add(new MealItem(
                    scored.meal().id(),
                    scored.meal().sourceType(),
                    scored.meal().ownerUserId(),
                    scored.meal().name(),
                    scored.meal().slots(),
                    scored.score().finalScore()
            ));
            details.add(scored.score());
        }
        return new MealRankResult(List.copyOf(ranked), List.copyOf(details));
    }

    private ScoredMeal score(
            MealItem item,
            SlotBundle query,
            Map<String, Double> slotPreferenceScores,
            Map<Long, Double> mealFeedbackScores
    ) {
        double contextScore = slotScore(item.slots(), query);
        double preferenceScore = preferenceScore(item.slots(), slotPreferenceScores);
        double feedbackScore = normalizePersonalScore(mealFeedbackScores.get(item.id()));
        double finalScore = clamp(
                CONTEXT_WEIGHT * contextScore
                        + PREFERENCE_WEIGHT * preferenceScore
                        + FEEDBACK_WEIGHT * feedbackScore
        );
        return new ScoredMeal(item, new MealRankScore(
                item.id(), item.name(), contextScore, preferenceScore, feedbackScore, finalScore));
    }

    /**
     * 对餐食所有标签的长期偏好取均值；没有画像时返回中性分，避免干扰原有排序。
     */
    private double preferenceScore(SlotBundle slots, Map<String, Double> scores) {
        List<UserPreferenceService.SlotValue> values = userPreferenceService.slotValues(slots);
        List<Double> matched = values.stream()
                .map(value -> scores.get(userPreferenceService.key(value.slotName(), value.optionValue())))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (matched.isEmpty()) {
            return NEUTRAL_SCORE;
        }
        return normalizePersonalScore(matched.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    /** 将 [-10, 10] 的累计偏好或反馈原始分归一到 [0, 1]。 */
    private double normalizePersonalScore(Double rawScore) {
        if (rawScore == null) {
            return NEUTRAL_SCORE;
        }
        return clamp((Math.max(-MAX_RAW_PERSONAL_SCORE, Math.min(MAX_RAW_PERSONAL_SCORE, rawScore))
                + MAX_RAW_PERSONAL_SCORE) / (2 * MAX_RAW_PERSONAL_SCORE));
    }

    /** 计算餐食 slots 与查询 slots 的 7 维平均重叠比例。 */
    private double slotScore(SlotBundle item, SlotBundle query) {
        SlotBundle safeQuery = query == null ? SlotBundle.empty() : query;
        // 7 维各自算 overlap 后求和
        double total = overlap(item.mealTime(), safeQuery.mealTime())
                + overlap(item.mood(), safeQuery.mood())
                + overlap(item.scene(), safeQuery.scene())
                + overlap(item.healthGoal(), safeQuery.healthGoal())
                + overlap(item.cuisine(), safeQuery.cuisine())
                + overlap(item.taste(), safeQuery.taste())
                + overlap(item.convenience(), safeQuery.convenience());
        return clamp(total / 7.0); // 归一化到 [0,1]
    }

    /** 计算 queryValues 中有多少标签出现在 itemValues 中，返回命中比例。 */
    private double overlap(List<String> itemValues, List<String> queryValues) {
        if (queryValues == null || queryValues.isEmpty()) {
            return 0; // 查询侧该维度为空时不计分
        }
        Set<String> itemSet = Set.copyOf(itemValues == null ? List.of() : itemValues);
        long hits = queryValues.stream().filter(itemSet::contains).count();
        // hits * 1.0 / queryValues.size()  即  命中的用户标签数 / 用户查询标签总数
        // 例如 鸡胸肉的health_Goal有[清淡，高蛋白] 猪肘的health_Goal有[高蛋白]，用户的输入的health_Goal是[清淡，高蛋白]
        // 那这里 queryValues就是[清淡，高蛋白]
        // 鸡胸肉的 hits = 2   猪肘的 hits = 1
        // 于是最终得分，鸡胸肉的 score = 2 / 2 = 1,  猪肘的 score = 1 / 2 = 0.5 分
        // 排序的规则就是看 谁更能满足用户的需求
        return hits * 1.0 / queryValues.size();
    }

    /** 将分数约束在 [0, 1] 区间。 */
    private double clamp(double score) {
        return Math.max(0, Math.min(1, score));
    }

    private record ScoredMeal(MealItem meal, MealRankScore score) {
    }
}
