package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * LINE 授權完導回前端時，網址上帶的 code 和 state，由前端轉交給後端。
 */
public record LineLoginRequest(@NotBlank(message = "缺少 LINE 登入資料") String code,
                               @NotBlank(message = "缺少 LINE 登入資料") String state) {

    /** 不印 code */
    @Override
    public String toString() {
        return "LineLoginRequest[]";
    }
}
