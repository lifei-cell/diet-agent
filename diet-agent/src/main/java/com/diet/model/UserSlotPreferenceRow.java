package com.diet.model;

import lombok.Data;

/** 用户在单个槽位选项上的累计偏好。 */
@Data
public class UserSlotPreferenceRow {
    private String slotName;
    private String optionValue;
    private Double preferenceScore;
}
