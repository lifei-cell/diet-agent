package com.diet.mapper;

import com.diet.model.UserHealthProfileRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserHealthProfileMapper {
    UserHealthProfileRow findByUserId(@Param("userId") Long userId);

    int upsert(UserHealthProfileRow row);
}
