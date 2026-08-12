package com.diet.service.meal;

import com.diet.exception.DietException;
import com.diet.mapper.MealMapper;
import com.diet.model.MealItem;
import com.diet.model.MealItemRow;
import com.diet.model.MealRequest;
import com.diet.model.NutritionConstraints;
import com.diet.model.NutritionInfo;
import com.diet.model.SlotBundle;
import com.diet.enums.SourceMode;
import com.diet.service.slot.SlotOptionService;
import com.diet.util.JsonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 餐食数据服务。
 * 提供 CRUD 和基于 MySQL JSON_OVERLAPS 的标签检索；Orchestrator 推荐链路通过 {@link #search} 召回候选。
 */
@Service
public class MealService implements MealCandidateRepository {

    /** 单次检索从 DB 拉取的最大行数，初排后取 top10 交给 Rank 层。 */
    private static final int SEARCH_LIMIT = 50;
    private final MealMapper mealMapper;
    private final MealSlotTagService mealSlotTagService;
    private final SlotOptionService slotOptionService;
    private final JsonService jsonService;
    public MealService(
            MealMapper mealMapper,
            MealSlotTagService mealSlotTagService,
            SlotOptionService slotOptionService,
            JsonService jsonService
    ) {
        this.mealMapper = mealMapper;
        this.mealSlotTagService = mealSlotTagService;
        this.slotOptionService = slotOptionService;
        this.jsonService = jsonService;
    }

    public List<MealItem> findPersonalMeals(Long userId) {
        return mealMapper.findPersonalMeals(userId).stream().map(this::toMealItem).toList();
    }

    public List<MealItem> findPublicMeals() {
        return mealMapper.findPublicMeals().stream().map(this::toMealItem).toList();
    }

    /**
     * 查询当前用户可以反馈的餐食：公共库餐食或归属自己的个人餐食。
     */
    public MealItem findAccessibleMealById(Long userId, Long mealId) {
        if (userId == null || mealId == null) {
            return null;
        }
        return toMealItem(mealMapper.findAccessibleById(mealId, userId));
    }

    /**
     * PERSONAL 模式空库前置检查。
     * 由 Orchestrator#handleTurn 调用，count > 0 才继续推荐链路。
     */
    public boolean hasPersonalMeals(Long userId) {
        return mealMapper.countPersonalMeals(userId) > 0; // 查个人餐食数量是否大于 0
    }

    @Transactional
    public MealItem createPersonalMeal(Long userId, MealRequest request) {
        validateMealRequest(request);
        MealItemRow row = toRow(null, SourceMode.PERSONAL, userId, request);
        mealMapper.insert(row);
        mealSlotTagService.replaceTags(row.getId(), request.toSlots());
        return toMealItem(row);
    }

    @Transactional
    public MealItem updatePersonalMeal(Long userId, Long mealId, MealRequest request) {
        validateMealRequest(request);
        MealItemRow row = toRow(mealId, SourceMode.PERSONAL, userId, request);
        int updated = mealMapper.updatePersonal(row);
        if (updated == 0) {
            throw new DietException("个人餐食不存在或无权限修改");
        }
        mealSlotTagService.replaceTags(mealId, request.toSlots());
        return toMealItem(mealMapper.findPersonalById(mealId, userId));
    }

    @Transactional
    public void deletePersonalMeal(Long userId, Long mealId) {
        // 源记录删除成功后清理派生索引；整个方法受 @Transactional 保护，可一起回滚。
        int deleted = mealMapper.deletePersonal(mealId, userId);
        if (deleted == 0) {
            throw new DietException("个人餐食不存在或无权限删除");
        }
        mealSlotTagService.removeTags(mealId);
    }

    /**
     * 兼容旧调用的结构化召回入口。新的混合召回由 HybridMealRetrievalService 组合多个通道。
     */
    public List<MealItem> search(SourceMode sourceMode, Long userId, SlotBundle slots, NutritionConstraints nutritionConstraints) {
        return searchStructured(sourceMode, userId, slots, nutritionConstraints);
    }

    /**
     * 按标准化后的槽位标签进行结构化召回；营养和过敏原条件始终在数据库层硬过滤。
     */
    public List<MealItem> searchStructured(SourceMode sourceMode, Long userId, SlotBundle slots, NutritionConstraints nutritionConstraints) {
        NutritionConstraints safeConstraints = NutritionConstraints.sanitize(nutritionConstraints);
        SlotBundle safeSlots = slots == null ? SlotBundle.empty() : slots;
        // 从 meal_slot_tag 倒排索引查 7 维标签，避免对 meal_item JSON 列全表 JSON_OVERLAPS 扫描。
        List<MealItemRow> rows = mealMapper.searchBySlotIndex(
                sourceMode,                                      // PERSONAL 或 PUBLIC，决定查哪张数据
                userId,                                          // PERSONAL 时过滤 owner_user_id
                safeSlots.mealTime(),
                safeSlots.mood(),
                safeSlots.scene(),
                safeSlots.healthGoal(),
                safeSlots.cuisine(),
                safeSlots.taste(),
                safeSlots.convenience(),
                safeConstraints.maxEnergyKcal(),
                safeConstraints.minProteinG(),
                safeConstraints.maxFatG(),
                safeConstraints.maxCarbohydrateG(),
                safeConstraints.maxSodiumMg(),
                jsonService.toJsonArray(safeConstraints.excludedAllergens()),
                SEARCH_LIMIT                                     // DB 层最多返回 50 行
        );
        // Row → MealItem
        return rows.stream().map(this::toMealItem).toList();
    }

    /**
     * 为应用侧 BM25 提供受限语料。数据库只负责权限与硬约束过滤，避免 BM25 将不合规餐食带回候选集。
     */
    @Override
    public List<MealItem> searchKeywordCorpus(
            SourceMode sourceMode,
            Long userId,
            NutritionConstraints nutritionConstraints,
            int limit
    ) {
        NutritionConstraints safeConstraints = NutritionConstraints.sanitize(nutritionConstraints);
        int safeLimit = Math.max(20, Math.min(2_000, limit));
        return mealMapper.searchKeywordCorpus(
                        sourceMode,
                        userId,
                        safeConstraints.maxEnergyKcal(),
                        safeConstraints.minProteinG(),
                        safeConstraints.maxFatG(),
                        safeConstraints.maxCarbohydrateG(),
                        safeConstraints.maxSodiumMg(),
                        jsonService.toJsonArray(safeConstraints.excludedAllergens()),
                        safeLimit
                ).stream()
                .map(this::toMealItem)
                .toList();
    }

    /**
     * 从外部向量服务返回的 ID 中重新加载本库可访问的餐食。调用方还必须执行营养硬约束校验。
     */
    public List<MealItem> findAccessibleByIds(SourceMode sourceMode, Long userId, List<Long> ids) {
        if (sourceMode == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> safeIds = ids.stream().filter(java.util.Objects::nonNull).distinct().limit(100).toList();
        if (safeIds.isEmpty()) {
            return List.of();
        }
        return mealMapper.findAccessibleByIds(sourceMode, userId, safeIds).stream().map(this::toMealItem).toList();
    }

    private void validateMealRequest(MealRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new DietException("餐食名称不能为空");
        }
        SlotBundle slots = request.toSlots();
        if (slots.mealTime().isEmpty()) {
            throw new DietException("餐次至少选择一个标签");
        }
        slotOptionService.validate(slots);
        validateNutrition(request.nutrition());
    }

    private MealItemRow toRow(Long id, SourceMode sourceMode, Long ownerUserId, MealRequest request) {
        SlotBundle slots = request.toSlots();
        MealItemRow row = new MealItemRow();
        row.setId(id);
        row.setSourceType(sourceMode.name());
        row.setOwnerUserId(ownerUserId);
        row.setName(request.name().trim());
        row.setMealTime(jsonService.toJsonArray(slots.mealTime()));
        row.setMood(jsonService.toJsonArray(slots.mood()));
        row.setScene(jsonService.toJsonArray(slots.scene()));
        row.setHealthGoal(jsonService.toJsonArray(slots.healthGoal()));
        row.setCuisine(jsonService.toJsonArray(slots.cuisine()));
        row.setTaste(jsonService.toJsonArray(slots.taste()));
        row.setConvenience(jsonService.toJsonArray(slots.convenience()));
        NutritionInfo nutrition = normalizeNutrition(request.nutrition());
        row.setEnergyKcal(nutrition.energyKcal());
        row.setProteinG(nutrition.proteinG());
        row.setFatG(nutrition.fatG());
        row.setCarbohydrateG(nutrition.carbohydrateG());
        row.setFiberG(nutrition.fiberG());
        row.setSodiumMg(nutrition.sodiumMg());
        row.setAllergens(jsonService.toJsonArray(nutrition.allergens()));
        row.setNutritionSource(nutrition.nutritionSource());
        return row;
    }

    private MealItem toMealItem(MealItemRow row) {
        if (row == null) {
            return null;
        }
        SlotBundle slots = new SlotBundle(
                jsonService.fromJsonArray(row.getMealTime()),
                jsonService.fromJsonArray(row.getMood()),
                jsonService.fromJsonArray(row.getScene()),
                jsonService.fromJsonArray(row.getHealthGoal()),
                jsonService.fromJsonArray(row.getCuisine()),
                jsonService.fromJsonArray(row.getTaste()),
                jsonService.fromJsonArray(row.getConvenience())
        );
        NutritionInfo nutrition = new NutritionInfo(
                row.getEnergyKcal(),
                row.getProteinG(),
                row.getFatG(),
                row.getCarbohydrateG(),
                row.getFiberG(),
                row.getSodiumMg(),
                jsonService.fromJsonArray(row.getAllergens()),
                row.getNutritionSource()
        );
        return new MealItem(
                row.getId(),
                SourceMode.valueOf(row.getSourceType()),
                row.getOwnerUserId(),
                row.getName(),
                slots,
                nutrition,
                0
        );
    }

    private void validateNutrition(NutritionInfo nutrition) {
        NutritionInfo safe = nutrition == null ? NutritionInfo.empty() : nutrition;
        validateNonNegative(safe.energyKcal(), "热量");
        validateNonNegative(safe.proteinG(), "蛋白质");
        validateNonNegative(safe.fatG(), "脂肪");
        validateNonNegative(safe.carbohydrateG(), "碳水化合物");
        validateNonNegative(safe.fiberG(), "膳食纤维");
        validateNonNegative(safe.sodiumMg(), "钠");
        if (safe.nutritionSource() != null && safe.nutritionSource().trim().length() > 64) {
            throw new DietException("营养数据来源不能超过 64 个字符");
        }
    }

    private void validateNonNegative(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new DietException(label + "必须是非负数");
        }
    }

    private NutritionInfo normalizeNutrition(NutritionInfo nutrition) {
        NutritionInfo safe = nutrition == null ? NutritionInfo.empty() : nutrition;
        List<String> allergens = safe.allergens() == null ? List.of() : safe.allergens().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        String source = safe.nutritionSource() == null || safe.nutritionSource().isBlank()
                ? null
                : safe.nutritionSource().trim();
        return new NutritionInfo(
                safe.energyKcal(),
                safe.proteinG(),
                safe.fatG(),
                safe.carbohydrateG(),
                safe.fiberG(),
                safe.sodiumMg(),
                allergens,
                source
        );
    }
}
