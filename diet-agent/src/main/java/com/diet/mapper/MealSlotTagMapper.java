package com.diet.mapper;

import com.diet.model.MealSlotTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MealSlotTagMapper {
    int deleteByMealId(@Param("mealId") Long mealId);

    int insertBatch(@Param("tags") List<MealSlotTag> tags);
}
