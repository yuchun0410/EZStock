package com.ezstock.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LINE 登入（LINE Login channel，跟 LINE Bot 的 Messaging API channel 是不同的 channel）。
 * channelSecret 是密碼，由環境變數 EZSTOCK_LINE_LOGIN_CHANNEL_SECRET 帶入。
 *
 * @param channelId     LINE Login channel 的 Channel ID
 * @param channelSecret LINE Login channel 的 Channel secret（用 code 換 token 時要帶）
 * @param redirectUri   LINE 授權完要導回的前端網址，必須跟 LINE Developers 後台填的 Callback URL 完全一樣
 */
@ConfigurationProperties(prefix = "ezstock.line-login")
public record LineLoginProperties(String channelId, String channelSecret, String redirectUri) {

    public boolean enabled() {
        return notBlank(channelId) && notBlank(channelSecret) && notBlank(redirectUri);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 不印密碼 */
    @Override
    public String toString() {
        return "LineLoginProperties[enabled=" + enabled() + ", redirectUri=" + redirectUri + "]";
    }
}
