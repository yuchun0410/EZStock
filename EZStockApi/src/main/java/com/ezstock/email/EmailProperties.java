package com.ezstock.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 寄信相關設定。
 *
 * @param verifyUrl 驗證信裡的連結要導到前端的哪一頁，後面會接上 ?token=…；沒設定時用本機前端的網址
 */
@ConfigurationProperties(prefix = "ezstock.email")
public record EmailProperties(String verifyUrl) {

    static final String DEFAULT_VERIFY_URL = "http://localhost:5173/verify-email";

    public EmailProperties {
        if (verifyUrl == null || verifyUrl.isBlank()) {
            verifyUrl = DEFAULT_VERIFY_URL;
        }
    }
}
