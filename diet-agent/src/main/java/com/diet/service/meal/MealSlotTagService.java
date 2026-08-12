package com.diet.service.meal;

import com.diet.mapper.MealSlotTagMapper;
import com.diet.model.MealSlotTag;
import com.diet.model.SlotBundle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 维护 meal_item JSON 标签的派生倒排索引。JSON 是兼容已有 API 的源数据，
 * meal_slot_tag 是查询侧索引；两者在同一事务中更新。
 */
@Service
public class MealSlotTagService {
    private final MealSlotTagMapper mealSlotTagMapper;

    public MealSlotTagService(MealSlotTagMapper mealSlotTagMapper) {
        this.mealSlotTagMapper = mealSlotTagMapper;
    }

    public void replaceTags(Long mealId, SlotBundle slots) {
        if (mealId == null) {
            return;
        }
        mealSlotTagMapper.deleteByMealId(mealId);
        List<MealSlotTag> tags = tagsFor(mealId, slots);
        if (!tags.isEmpty()) {
            mealSlotTagMapper.insertBatch(tags);
        }
    }

    public void removeTags(Long mealId) {
        if (mealId != null) {
            mealSlotTagMapper.deleteByMealId(mealId);
        }
    }

    private List<MealSlotTag> tagsFor(Long mealId, SlotBundle slots) {
        SlotBundle safe = slots == null ? SlotBundle.empty() : slots;
        List<MealSlotTag> tags = new ArrayList<>();
        append(tags, mealId, "mealTime", safe.mealTime());
        append(tags, mealId, "mood", safe.mood());
        append(tags, mealId, "scene", safe.scene());
        append(tags, mealId, "healthGoal", safe.healthGoal());
        append(tags, mealId, "cuisine", safe.cuisine());
        append(tags, mealId, "taste", safe.taste());
        append(tags, mealId, "convenience", safe.convenience());
        return tags;
    }

    private void append(List<MealSlotTag> target, Long mealId, String slotName, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .forEach(value -> target.add(new MealSlotTag(mealId, slotName, value.trim())));
    }
}
