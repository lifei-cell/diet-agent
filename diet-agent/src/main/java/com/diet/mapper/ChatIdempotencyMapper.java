package com.diet.mapper;

import com.diet.model.ChatIdempotencyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ChatIdempotencyMapper {
    ChatIdempotencyRow find(@Param("userId") Long userId, @Param("requestId") String requestId);

    int insert(ChatIdempotencyRow row);

    int takeOver(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("requestHash") String requestHash,
            @Param("processingToken") String processingToken,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    int complete(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("processingToken") String processingToken,
            @Param("responseJson") String responseJson,
            @Param("traceId") String traceId
    );

    int fail(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("processingToken") String processingToken,
            @Param("failureCode") String failureCode
    );
}
