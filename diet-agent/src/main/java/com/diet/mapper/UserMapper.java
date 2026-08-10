package com.diet.mapper;

import com.diet.model.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    AppUser findById(@Param("id") Long id);

    AppUser findByUsername(@Param("username") String username);

    int insert(AppUser user);
}
