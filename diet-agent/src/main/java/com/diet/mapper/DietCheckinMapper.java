package com.diet.mapper;

import com.diet.model.CheckinDraftRow;
import com.diet.model.DietCheckinItemRow;
import com.diet.model.DietCheckinRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DietCheckinMapper {
    int insertDraft(CheckinDraftRow draft);

    CheckinDraftRow findDraftByIdAndUserId(@Param("id") String id, @Param("userId") Long userId);

    int deleteDraftByIdAndUserId(@Param("id") String id, @Param("userId") Long userId);

    int insertCheckin(DietCheckinRow checkin);

    int insertCheckinItem(DietCheckinItemRow item);

    List<DietCheckinRow> findCheckinsByUserAndDate(@Param("userId") Long userId, @Param("checkinDate") LocalDate checkinDate);

    List<DietCheckinItemRow> findItemsByCheckinId(@Param("checkinId") Long checkinId);

    DietCheckinRow findCheckinByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int deleteCheckinByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int deleteCheckinItemsByCheckinId(@Param("checkinId") Long checkinId);
}
