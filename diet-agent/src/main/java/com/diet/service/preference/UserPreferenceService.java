package com.diet.service.preference;

import com.diet.enums.FeedbackAction;
import com.diet.mapper.FeedbackMapper;
import com.diet.mapper.UserPreferenceMapper;
import com.diet.model.MealFeedbackScoreRow;
import com.diet.model.MealItem;
import com.diet.model.SlotBundle;
import com.diet.model.UserSlotPreferenceRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护用户长期饮食偏好，并为餐食重排提供画像与历史反馈分。
 */
@Service
public class UserPreferenceService {

    private static final String KEY_SEPARATOR = "\u0001";

    private final UserPreferenceMapper userPreferenceMapper;
    private final FeedbackMapper feedbackMapper;

    public UserPreferenceService(UserPreferenceMapper userPreferenceMapper, FeedbackMapper feedbackMapper) {
        this.userPreferenceMapper = userPreferenceMapper;
        this.feedbackMapper = feedbackMapper;
    }

    /**
     * 将一次餐食反馈扩散到该餐食携带的全部槽位标签，实时更新用户画像。
     */
    public void applyFeedback(Long userId, MealItem meal, FeedbackAction action, Integer rating) {
        if (userId == null || meal == null || meal.slots() == null || action == null) {
            return;
        }
        double delta = action.preferenceWeight(rating);
        if (delta == 0) {
            return;
        }
        int positiveIncrement = delta > 0 ? 1 : 0;
        int negativeIncrement = delta < 0 ? 1 : 0;
        for (SlotValue slotValue : slotValues(meal.slots())) {
            userPreferenceMapper.upsertSlotPreference(
                    userId,
                    slotValue.slotName(),
                    slotValue.optionValue(),
                    delta,
                    positiveIncrement,
                    negativeIncrement
            );
        }
    }

    /** 返回 key=slotName + 分隔符 + optionValue 的偏好原始分。 */
    public Map<String, Double> slotPreferenceScores(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (UserSlotPreferenceRow row : userPreferenceMapper.findSlotPreferences(userId)) {
            if (row.getSlotName() != null && row.getOptionValue() != null) {
                scores.put(key(row.getSlotName(), row.getOptionValue()),
                        row.getPreferenceScore() == null ? 0.0 : row.getPreferenceScore());
            }
        }
        return scores;
    }

    /** 返回候选餐食的历史聚合反馈原始分。 */
    public Map<Long, Double> mealFeedbackScores(Long userId, List<Long> itemIds) {
        if (userId == null || itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (MealFeedbackScoreRow row : feedbackMapper.findMealFeedbackScores(userId, itemIds)) {
            if (row.getItemId() != null) {
                scores.put(row.getItemId(), row.getFeedbackScore() == null ? 0.0 : row.getFeedbackScore());
            }
        }
        return scores;
    }

    public List<SlotValue> slotValues(SlotBundle slots) {
        if (slots == null) {
            return List.of();
        }
        List<SlotValue> values = new ArrayList<>();
        append(values, "mealTime", slots.mealTime());
        append(values, "mood", slots.mood());
        append(values, "scene", slots.scene());
        append(values, "healthGoal", slots.healthGoal());
        append(values, "cuisine", slots.cuisine());
        append(values, "taste", slots.taste());
        append(values, "convenience", slots.convenience());
        return values;
    }

    public String key(String slotName, String optionValue) {
        return slotName + KEY_SEPARATOR + optionValue;
    }

    private void append(List<SlotValue> target, String slotName, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> target.add(new SlotValue(slotName, value)));
    }

    public record SlotValue(String slotName, String optionValue) {
    }
}
