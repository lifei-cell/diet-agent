package com.diet.model;

import java.util.Map;

import com.diet.enums.SourceMode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {
    private String sessionId;
    private String message;
    private SourceMode sourceMode;
    private Map<String, Object> context;
    /** 可选的结构化硬约束；未提供时由 IntentAgent 从 message 中抽取。 */
    private NutritionConstraints nutritionConstraints;
}
