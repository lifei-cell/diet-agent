package com.diet.service.meal;

import com.diet.model.SlotBundle;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 仅在查询侧扩展受控同义词，不修改数据库中的标准槽位标签。
 *
 * <p>这样既可处理“低脂/少油/减脂”“热乎/粥汤”等表达差异，又不会把非标准标签写入
 * meal_item，破坏 {@code diet_slot_option} 的数据治理。</p>
 */
@Component
public class SlotQueryExpansionService {

    private static final Map<String, Map<String, List<String>>> SYNONYMS = Map.of(
            "healthGoal", Map.of(
                    "减脂", List.of("减脂", "低脂", "低油", "清淡"),
                    "低脂", List.of("低脂", "减脂", "低油", "清淡"),
                    "低油", List.of("低油", "低脂", "减脂", "清淡"),
                    "高蛋白", List.of("高蛋白", "补能", "均衡"),
                    "低糖", List.of("低糖", "控碳水", "低油"),
                    "控碳水", List.of("控碳水", "低糖", "低油"),
                    "养胃", List.of("养胃", "暖胃", "易消化", "清淡")
            ),
            "cuisine", Map.of(
                    "粥汤", List.of("粥汤", "粉面", "家常"),
                    "轻食", List.of("轻食", "西餐", "素食"),
                    "快餐", List.of("快餐", "小吃", "粉面")
            ),
            "taste", Map.of(
                    "清淡", List.of("清淡", "咸鲜", "番茄味"),
                    "麻辣", List.of("麻辣", "辣", "中辣"),
                    "辣", List.of("辣", "微辣", "中辣", "麻辣")
            ),
            "scene", Map.of(
                    "运动后", List.of("运动后", "工作", "家里"),
                    "聚餐", List.of("聚餐", "周末", "夜宵")
            ),
            "convenience", Map.of(
                    "快速", List.of("快速", "少排队", "外带方便", "一人食"),
                    "外带方便", List.of("外带方便", "快速", "少餐具", "适合边走边吃")
            )
    );

    /** 返回用于结构化召回的扩展槽位；原槽位会始终保留在结果首位。 */
    public SlotBundle expand(SlotBundle slots) {
        SlotBundle safe = slots == null ? SlotBundle.empty() : slots;
        return new SlotBundle(
                expand("mealTime", safe.mealTime()),
                expand("mood", safe.mood()),
                expand("scene", safe.scene()),
                expand("healthGoal", safe.healthGoal()),
                expand("cuisine", safe.cuisine()),
                expand("taste", safe.taste()),
                expand("convenience", safe.convenience())
        );
    }

    /**
     * 为关键词通道生成标准标签和扩展标签。关键词通道只用它们召回，不用它们替代硬约束。
     */
    public List<String> expandedValues(SlotBundle slots) {
        SlotBundle expanded = expand(slots);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(expanded.mealTime());
        values.addAll(expanded.mood());
        values.addAll(expanded.scene());
        values.addAll(expanded.healthGoal());
        values.addAll(expanded.cuisine());
        values.addAll(expanded.taste());
        values.addAll(expanded.convenience());
        return List.copyOf(values);
    }

    private List<String> expand(String slotName, List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Map<String, List<String>> byValue = SYNONYMS.getOrDefault(slotName, Map.of());
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim();
            result.add(normalized);
            result.addAll(byValue.getOrDefault(normalized, List.of()));
            // 仅为英文标签预留大小写归一能力，中文标签原样保持。
            result.addAll(byValue.getOrDefault(normalized.toLowerCase(Locale.ROOT), List.of()));
        }
        return List.copyOf(result);
    }
}
