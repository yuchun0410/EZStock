package com.ezstock.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh token 的 cookie。
 * - HttpOnly：JavaScript 讀不到，網站就算被 XSS 也偷不走
 * - SameSite=Strict：別的網站發出的請求不會帶這個 cookie，防 CSRF
 * - Path=/api/auth：只有換 token、登出時才會送出，其他 API 請求不會帶
 * - Secure：上線（HTTPS）時要開，本機 http 開了瀏覽器會不存
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "ezstock_refresh";
    private static final String PATH = "/api/auth";

    private final JwtProperties properties;

    public RefreshTokenCookies(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(properties.refreshTokenTtl()).build();
    }

    /** 登出或 token 無效時，叫瀏覽器刪掉 cookie */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
