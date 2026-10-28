package com.ezstock.exception;

/**
 * 所有錯誤回應的統一格式，例如：
 * {"status":404,"error":"NOT_FOUND","message":"查無股票 9999","traceId":"a1b2c3d4"}
 */
public record ApiError(int status, String error, String message, String traceId) {
}
