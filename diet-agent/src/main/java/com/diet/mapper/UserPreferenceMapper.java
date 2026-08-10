package com.diet.mapper;

import com.diet.model.UserSlotPreferenceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPreferenceMapper {

    List<UserSlotPreferenceRow> findSlotPreferences(@Param("userId") Long userId);

    int upsertSlotPreference(
            @Param("userId") Long userId,
            @Param("slotName") String slotName,
            @Param("optionValue") String optionValue,
            @Param("delta") double delta,
            @Param("positiveIncrement") int positiveIncrement,
            @Param("negativeIncrement") int negativeIncrement
    );
}
