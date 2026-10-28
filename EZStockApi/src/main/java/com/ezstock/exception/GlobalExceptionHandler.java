package com.ezstock.exception;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.datasource.finmind.FinMindApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

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

    /**
     * 網址打錯（或瀏覽器自動要 /favicon.ico）時，Spring 會丟 NoResourceFoundException。
     * 這是使用者端的錯誤，回 404 並記 WARN，不能被下面的 Exception 接走變成 500 + ERROR 堆疊。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        String message = "找不到 " + request.getRequestURI();
        log.warn("查無路徑：{} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
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

    /** @Valid 檢查沒過：把每個欄位的錯誤訊息串起來，例如「請選擇起始日；投入金額至少 1,000 元」 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("；"));
        log.warn("參數錯誤：{}", message);
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    /** JSON 格式錯誤，或欄位型別不對（例如日期寫成 2026/01/01） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        // 例外訊息可能包含類別名稱等內部資訊，只寫進 log，不回給前端
        log.warn("參數錯誤：請求內容無法解析：{}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "請求內容格式錯誤，日期請用 yyyy-MM-dd");
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
