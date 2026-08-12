package com.diet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.diet")
public class DietExceptionHandler {
    @ExceptionHandler(DietException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNewDietException(DietException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(SessionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSessionConflict(SessionConflictException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(SessionCoordinatorUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleSessionCoordinatorUnavailable(SessionCoordinatorUnavailableException e) {
        return Map.of("message", "会话协调服务暂不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleException(Exception e) {
        return Map.of("message", e.getMessage() == null ? "服务异常" : e.getMessage());
    }
}
