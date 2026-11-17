package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param token 驗證信連結裡的 token
 */
public record VerifyEmailRequest(@NotBlank(message = "缺少驗證碼") String token) {

    /** 不印 token */
    @Override
    public String toString() {
        return "VerifyEmailRequest[]";
    }
}
