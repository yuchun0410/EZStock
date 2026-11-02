package com.ezstock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "請輸入 Email")
        @Email(message = "Email 格式不正確")
        @Size(max = 254, message = "Email 太長")
        String email,

        @NotBlank(message = "請輸入密碼")
        @Size(min = 8, max = 64, message = "密碼長度要 8～64 個字")
        String password,

        @NotBlank(message = "請輸入暱稱")
        @Size(max = 50, message = "暱稱最多 50 個字")
        String displayName) {

    /** 不印密碼 */
    @Override
    public String toString() {
        return "RegisterRequest[displayName=" + displayName + "]";
    }
}
