package com.ezstock.line;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LINE Messaging API 設定，兩個值都是密碼，由環境變數帶入：
 * EZSTOCK_LINE_CHANNEL_SECRET、EZSTOCK_LINE_CHANNEL_TOKEN。
 *
 * @param channelSecret 驗證 webhook 簽章用（確認請求真的是 LINE 送來的）
 * @param channelToken  呼叫 LINE API 回覆、推播訊息用
 * @param botId         Bot 的 LINE ID（例如 @878jfbhc，不是密碼），網站顯示「加好友」連結用
 */
@ConfigurationProperties(prefix = "ezstock.line")
public record LineProperties(String channelSecret, String channelToken, String botId) {

    public boolean enabled() {
        return channelSecret != null && !channelSecret.isBlank() && channelToken != null && !channelToken.isBlank();
    }

    /** 不印密碼 */
    @Override
    public String toString() {
        return "LineProperties[enabled=" + enabled() + "]";
    }
}
