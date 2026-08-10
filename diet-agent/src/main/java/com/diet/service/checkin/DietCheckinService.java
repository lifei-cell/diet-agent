package com.diet.service.checkin;

import com.diet.exception.DietException;
import com.diet.mapper.DietCheckinMapper;
import com.diet.model.CheckinConfirmRequest;
import com.diet.model.CheckinDraftRow;
import com.diet.model.CheckinItemRequest;
import com.diet.model.CheckinItemResponse;
import com.diet.model.DailyCheckinSummary;
import com.diet.model.DietCheckinItemRow;
import com.diet.model.DietCheckinResponse;
import com.diet.model.DietCheckinRow;
import com.diet.model.FoodRecognitionItem;
import com.diet.model.ImageRecognitionResponse;
import com.diet.model.NutritionTarget;
import com.diet.model.NutritionTotals;
import com.diet.service.profile.UserHealthProfileService;
import com.diet.service.storage.CheckinImageStorage;
import com.diet.util.JsonService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DietCheckinService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> MEAL_TIMES = Set.of("早餐", "午餐", "晚餐", "加餐");
    private static final TypeReference<List<FoodRecognitionItem>> RECOGNITION_ITEMS = new TypeReference<>() {
    };

    private final DietCheckinMapper checkinMapper;
    private final FoodImageRecognitionService recognitionService;
    private final UserHealthProfileService profileService;
    private final JsonService jsonService;
    private final CheckinImageStorage imageStorage;

    public DietCheckinService(
            DietCheckinMapper checkinMapper,
            FoodImageRecognitionService recognitionService,
            UserHealthProfileService profileService,
            JsonService jsonService,
            CheckinImageStorage imageStorage
    ) {
        this.checkinMapper = checkinMapper;
        this.recognitionService = recognitionService;
        this.profileService = profileService;
        this.jsonService = jsonService;
        this.imageStorage = imageStorage;
    }

    public ImageRecognitionResponse recognize(Long userId, MultipartFile image) {
        ImagePayload payload = validateImage(image);
        FoodImageRecognitionService.RecognitionResult result = recognitionService.recognize(payload.data(), payload.mediaType());
        CheckinDraftRow draft = new CheckinDraftRow();
        draft.setId(UUID.randomUUID().toString());
        draft.setUserId(userId);
        String objectKey = imageStorage.storeForCheckin(userId, draft.getId(), payload.data(), payload.mediaType());
        draft.setImageObjectKey(objectKey);
        draft.setImageMediaType(payload.mediaType());
        draft.setRecognizedItems(jsonService.toJson(result.items()));
        draft.setAutomated(result.automated());
        draft.setMessage(result.message());
        try {
            checkinMapper.insertDraft(draft);
        } catch (RuntimeException exception) {
            imageStorage.remove(objectKey);
            throw exception;
        }
        return new ImageRecognitionResponse(draft.getId(), result.automated(), result.message(), result.items(), totalsOfRecognition(result.items()));
    }

    @Transactional
    public DietCheckinResponse confirm(Long userId, CheckinConfirmRequest request) {
        validateConfirmRequest(request);
        CheckinDraftRow draft = checkinMapper.findDraftByIdAndUserId(request.recognitionId(), userId);
        if (draft == null) {
            throw new DietException("识别草稿不存在，或不属于当前用户");
        }
        if (draft.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            checkinMapper.deleteDraftByIdAndUserId(draft.getId(), userId);
            imageStorage.remove(draft.getImageObjectKey());
            throw new DietException("识别草稿已过期，请重新上传图片");
        }
        List<CheckinItemRequest> items = normalizeItems(request.items());
        NutritionTotals totals = totalsOfRequests(items);

        DietCheckinRow checkin = new DietCheckinRow();
        checkin.setUserId(userId);
        checkin.setCheckinDate(request.checkinDate());
        checkin.setMealTime(request.mealTime().trim());
        checkin.setImageObjectKey(draft.getImageObjectKey());
        checkin.setImageMediaType(draft.getImageMediaType());
        checkin.setTotalEnergyKcal(totals.energyKcal());
        checkin.setTotalProteinG(totals.proteinG());
        checkin.setTotalFatG(totals.fatG());
        checkin.setTotalCarbohydrateG(totals.carbohydrateG());
        checkinMapper.insertCheckin(checkin);
        List<DietCheckinItemRow> savedItems = new ArrayList<>();
        for (CheckinItemRequest item : items) {
            DietCheckinItemRow row = toItemRow(checkin.getId(), item);
            checkinMapper.insertCheckinItem(row);
            savedItems.add(row);
        }
        checkinMapper.deleteDraftByIdAndUserId(draft.getId(), userId);
        return toResponse(checkin, savedItems);
    }

    public DailyCheckinSummary findDailySummary(Long userId, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        List<DietCheckinResponse> checkins = checkinMapper.findCheckinsByUserAndDate(userId, targetDate).stream()
                .map(row -> toResponse(row, checkinMapper.findItemsByCheckinId(row.getId())))
                .toList();
        NutritionTotals consumed = totalsOfResponses(checkins);
        NutritionTarget target = profileService.findNutritionTarget(userId);
        NutritionTotals remaining = target == null ? null : new NutritionTotals(
                round(target.dailyEnergyKcal() - consumed.energyKcal()),
                round(target.dailyProteinG() - consumed.proteinG()),
                round(target.dailyFatG() - consumed.fatG()),
                round(target.dailyCarbohydrateG() - consumed.carbohydrateG())
        );
        String message = target == null
                ? "完善健康档案后，可查看今日摄入与个人每日营养目标的差距。"
                : "图片识别与营养数值均为估算，请根据实际份量、油盐和酱料进行调整。";
        return new DailyCheckinSummary(targetDate, checkins, consumed, target, remaining, message);
    }

    @Transactional
    public void delete(Long userId, Long checkinId) {
        DietCheckinRow checkin = checkinMapper.findCheckinByIdAndUserId(checkinId, userId);
        if (checkin == null) {
            throw new DietException("打卡记录不存在，或不属于当前用户");
        }
        imageStorage.remove(checkin.getImageObjectKey());
        checkinMapper.deleteCheckinByIdAndUserId(checkinId, userId);
        checkinMapper.deleteCheckinItemsByCheckinId(checkinId);
    }

    public CheckinImageStorage.StoredImage loadImage(Long userId, Long checkinId) {
        DietCheckinRow checkin = checkinMapper.findCheckinByIdAndUserId(checkinId, userId);
        if (checkin == null) {
            throw new DietException("打卡图片不存在，或不属于当前用户");
        }
        return imageStorage.load(checkin.getImageObjectKey(), checkin.getImageMediaType());
    }

    private ImagePayload validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new DietException("请选择一张餐食图片");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new DietException("图片不能超过 5MB");
        }
        String mediaType = image.getContentType();
        if (mediaType == null || !ALLOWED_MEDIA_TYPES.contains(mediaType.toLowerCase())) {
            throw new DietException("仅支持 JPG、PNG 或 WEBP 图片");
        }
        try {
            return new ImagePayload(image.getBytes(), mediaType.toLowerCase());
        } catch (IOException exception) {
            throw new DietException("读取上传图片失败", exception);
        }
    }

    private void validateConfirmRequest(CheckinConfirmRequest request) {
        if (request == null || request.recognitionId() == null || request.recognitionId().isBlank()) {
            throw new DietException("请先上传图片并完成识别");
        }
        if (request.checkinDate() == null || request.checkinDate().isAfter(LocalDate.now()) || request.checkinDate().isBefore(LocalDate.now().minusDays(30))) {
            throw new DietException("打卡日期须在最近 30 天内，且不能晚于今天");
        }
        if (request.mealTime() == null || !MEAL_TIMES.contains(request.mealTime().trim())) {
            throw new DietException("请选择早餐、午餐、晚餐或加餐");
        }
    }

    private List<CheckinItemRequest> normalizeItems(List<CheckinItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > 12) {
            throw new DietException("请确认 1 到 12 项菜品后再保存");
        }
        List<CheckinItemRequest> normalized = new ArrayList<>();
        for (CheckinItemRequest item : items) {
            String name = item == null || item.name() == null ? "" : item.name().trim();
            if (name.isBlank() || name.length() > 128) {
                throw new DietException("菜品名称不能为空，且不能超过 128 个字符");
            }
            normalized.add(new CheckinItemRequest(
                    name,
                    requiredNumber(item.estimatedWeightG(), 0, 3000, "估算份量"),
                    requiredNumber(item.energyKcal(), 0, 20000, "热量"),
                    requiredNumber(item.proteinG(), 0, 2000, "蛋白质"),
                    requiredNumber(item.fatG(), 0, 2000, "脂肪"),
                    requiredNumber(item.carbohydrateG(), 0, 3000, "碳水化合物"),
                    optionalNumber(item.confidence(), 0, 1, 0),
                    item.nutritionSource() == null || item.nutritionSource().isBlank() ? "USER_CONFIRMED" : item.nutritionSource().trim()
            ));
        }
        return normalized;
    }

    private Double requiredNumber(Double value, double min, double max, String label) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw new DietException(label + "数值不在合理范围内");
        }
        return round(value);
    }

    private Double optionalNumber(Double value, double min, double max, double fallback) {
        if (value == null) {
            return fallback;
        }
        return requiredNumber(value, min, max, "置信度");
    }

    private DietCheckinItemRow toItemRow(Long checkinId, CheckinItemRequest item) {
        DietCheckinItemRow row = new DietCheckinItemRow();
        row.setCheckinId(checkinId);
        row.setFoodName(item.name());
        row.setEstimatedWeightG(item.estimatedWeightG());
        row.setEnergyKcal(item.energyKcal());
        row.setProteinG(item.proteinG());
        row.setFatG(item.fatG());
        row.setCarbohydrateG(item.carbohydrateG());
        row.setConfidence(item.confidence());
        row.setNutritionSource(item.nutritionSource());
        return row;
    }

    private DietCheckinResponse toResponse(DietCheckinRow checkin, List<DietCheckinItemRow> items) {
        return new DietCheckinResponse(
                checkin.getId(),
                checkin.getCheckinDate(),
                checkin.getMealTime(),
                new NutritionTotals(checkin.getTotalEnergyKcal(), checkin.getTotalProteinG(), checkin.getTotalFatG(), checkin.getTotalCarbohydrateG()),
                items.stream().map(this::toItemResponse).toList(),
                checkin.getCreatedAt() == null ? null : checkin.getCreatedAt().toString()
        );
    }

    private CheckinItemResponse toItemResponse(DietCheckinItemRow item) {
        return new CheckinItemResponse(item.getId(), item.getFoodName(), item.getEstimatedWeightG(), item.getEnergyKcal(),
                item.getProteinG(), item.getFatG(), item.getCarbohydrateG(), item.getConfidence(), item.getNutritionSource());
    }

    private NutritionTotals totalsOfRecognition(List<FoodRecognitionItem> items) {
        return new NutritionTotals(sumRecognition(items, FoodRecognitionItem::energyKcal), sumRecognition(items, FoodRecognitionItem::proteinG),
                sumRecognition(items, FoodRecognitionItem::fatG), sumRecognition(items, FoodRecognitionItem::carbohydrateG));
    }

    private NutritionTotals totalsOfRequests(List<CheckinItemRequest> items) {
        return new NutritionTotals(sumRequests(items, CheckinItemRequest::energyKcal), sumRequests(items, CheckinItemRequest::proteinG),
                sumRequests(items, CheckinItemRequest::fatG), sumRequests(items, CheckinItemRequest::carbohydrateG));
    }

    private NutritionTotals totalsOfResponses(List<DietCheckinResponse> checkins) {
        return new NutritionTotals(
                round(checkins.stream().map(DietCheckinResponse::totals).mapToDouble(value -> value.energyKcal()).sum()),
                round(checkins.stream().map(DietCheckinResponse::totals).mapToDouble(value -> value.proteinG()).sum()),
                round(checkins.stream().map(DietCheckinResponse::totals).mapToDouble(value -> value.fatG()).sum()),
                round(checkins.stream().map(DietCheckinResponse::totals).mapToDouble(value -> value.carbohydrateG()).sum())
        );
    }

    private Double sumRecognition(List<FoodRecognitionItem> items, java.util.function.Function<FoodRecognitionItem, Double> getter) {
        return round(items.stream().map(getter).filter(value -> value != null).mapToDouble(Double::doubleValue).sum());
    }

    private Double sumRequests(List<CheckinItemRequest> items, java.util.function.Function<CheckinItemRequest, Double> getter) {
        return round(items.stream().map(getter).mapToDouble(Double::doubleValue).sum());
    }

    private Double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record ImagePayload(byte[] data, String mediaType) {
    }
}
