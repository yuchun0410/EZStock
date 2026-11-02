package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "請輸入 Email") String email,
        @NotBlank(message = "請輸入密碼") String password) {

    /** 不印密碼 */
    @Override
    public String toString() {
        return "LoginRequest[]";
    }
}
