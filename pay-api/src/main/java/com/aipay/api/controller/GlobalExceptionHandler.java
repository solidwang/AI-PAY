package com.aipay.api.controller;

import com.aipay.common.exception.BizException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBizException(BizException e) {
        return ResponseEntity.badRequest().body(
            Map.of("error", Map.of("code", e.getCode(), "message", e.getMessage()))
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        return ResponseEntity.internalServerError().body(
            Map.of("error", Map.of("code", "internal_error", "message", "Internal server error"))
        );
    }
}
