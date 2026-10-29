package com.ezstock.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 設定。
 *
 * @param privateKey     RS256 私鑰（PKCS#8、Base64 一行），由環境變數 EZSTOCK_JWT_PRIVATE_KEY 帶入；空的就用臨時金鑰
 * @param issuer         token 的發行者（iss），驗證時也會檢查
 * @param accessTokenTtl access token 效期
 */
@ConfigurationProperties(prefix = "ezstock.jwt")
public record JwtProperties(String privateKey, String issuer, Duration accessTokenTtl) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "ezstock";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
    }

    /** toString 不印私鑰（record 預設的 toString 會把所有欄位印出來） */
    @Override
    public String toString() {
        return "JwtProperties[privateKey=" + (privateKey == null || privateKey.isBlank() ? "未設定" : "已設定")
                + ", issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
    }
}
