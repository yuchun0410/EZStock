package com.ezstock.security;

/**
 * 驗證過的 LINE 使用者。
 *
 * @param userId LINE 使用者 id（ID Token 的 sub）；同一個 provider 底下的 LINE Login 和 LINE Bot 會拿到同一個值
 * @param email  要另外跟 LINE 申請權限、使用者也同意才會有，否則是 null
 */
public record LineLoginUser(String userId, String name, String pictureUrl, String email) {
}
