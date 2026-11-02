package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param credential 前端 Google 按鈕回傳的 ID Token
 */
public record GoogleLoginRequest(@NotBlank(message = "缺少 Google 登入資料") String credential) {

    /** 不印 token */
    @Override
    public String toString() {
        return "GoogleLoginRequest[]";
    }
}
