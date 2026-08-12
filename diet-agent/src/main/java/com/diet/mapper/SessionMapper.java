package com.diet.mapper;

import com.diet.model.SessionMessageRow;
import com.diet.model.SessionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SessionMapper {
    int insert(SessionRow row);

    SessionRow findById(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    SessionRow findByIdAnyUser(@Param("sessionId") String sessionId);

    int update(SessionRow row);

    int insertMessage(
            @Param("sessionId") String sessionId,
            @Param("role") String role,
            @Param("content") String content,
            @Param("intent") String intent,
            @Param("traceId") String traceId,
            @Param("requestId") String requestId
    );

    List<SessionMessageRow> listRecentMessages(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );
}




