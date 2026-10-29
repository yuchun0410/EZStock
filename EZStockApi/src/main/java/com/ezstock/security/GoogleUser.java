package com.ezstock.security;

/**
 * 從 Google ID Token 取出、已經驗證過的使用者資料。
 *
 * @param subject       Google 帳號的唯一編號（sub），Email 可以改，這個不會變，所以用它綁定會員
 * @param emailVerified Google 是否已確認這個 Email 屬於本人；沒確認的不能拿來綁定既有帳號
 */
public record GoogleUser(String subject, String email, boolean emailVerified, String name, String pictureUrl) {

    @Override
    public String toString() {
        return "GoogleUser[email=" + LogMasks.email(email) + ", emailVerified=" + emailVerified + "]";
    }
}
