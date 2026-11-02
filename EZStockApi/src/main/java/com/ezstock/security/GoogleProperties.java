package com.ezstock.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 登入設定。
 *
 * @param clientId Google OAuth 用戶端 ID；空白代表不開放 Google 登入
 */
@ConfigurationProperties(prefix = "ezstock.google")
public record GoogleProperties(String clientId) {

    public boolean enabled() {
        return clientId != null && !clientId.isBlank();
    }
}
