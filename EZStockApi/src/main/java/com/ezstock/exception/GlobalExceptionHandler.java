package com.ezstock.exception;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.datasource.finmind.FinMindApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 把例外統一轉成 ApiError 回應。
 * 可預期的錯誤（查無資料、參數錯誤）記 WARN；非預期的錯誤記 ERROR 並附上錯誤堆疊。
 * 回應裡不放錯誤堆疊，避免洩漏程式內部資訊。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        log.warn("查無資料：{}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException e) {
        log.warn("參數錯誤：{}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "缺少參數 " + e.getParameterName();
        log.warn("參數錯誤：{}", message);
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "參數 " + e.getName() + " 格式錯誤：" + e.getValue();
        log.warn("參數錯誤：{}", message);
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(FinMindApiException.class)
    public ResponseEntity<ApiError> handleFinMind(FinMindApiException e) {
        log.error("外部資料來源錯誤：{}", e.getMessage(), e);
        return build(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "外部資料來源暫時無法使用，請稍後再試");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("非預期錯誤：{}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "系統發生錯誤，請稍後再試");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message) {
        ApiError body = new ApiError(status.value(), error, message, MDC.get(RequestLoggingFilter.TRACE_ID));
        return ResponseEntity.status(status).body(body);
    }
}
