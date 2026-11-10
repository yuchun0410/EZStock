package com.ezstock.dto;

/**
 * 前端登入頁要顯示哪些登入方式。
 *
 * @param googleClientId   Google 用戶端 ID；null 代表沒開放 Google 登入，前端就不顯示 Google 按鈕
 * @param lineLoginEnabled 有沒有開放 LINE 登入
 */
public record AuthProvidersResponse(String googleClientId, boolean lineLoginEnabled) {
}
