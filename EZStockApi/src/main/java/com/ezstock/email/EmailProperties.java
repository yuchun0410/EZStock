package com.ezstock.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 寄信相關設定。寄信方式的優先順序：Apps Script → Mailjet → 都沒設定就寫進 log。
 *
 * @param verifyUrl  驗證信裡的連結要導到前端的哪一頁，後面會接上 ?token=…；沒設定時用本機前端的網址
 * @param from       Mailjet 的寄件人 Email，要先在 Mailjet 驗證過（Apps Script 用部署者自己的 Gmail，不用設）
 * @param fromName   寄件人名稱，收件人會看到這個名字
 * @param mailjet    Mailjet 的金鑰
 * @param appsScript Google Apps Script 網頁應用程式的網址與密語
 */
@ConfigurationProperties(prefix = "ezstock.email")
public record EmailProperties(String verifyUrl, String from, String fromName, Mailjet mailjet,
                              AppsScript appsScript) {

    static final String DEFAULT_VERIFY_URL = "http://localhost:5173/verify-email";
    static final String DEFAULT_FROM_NAME = "EZStock";

    public EmailProperties {
        if (verifyUrl == null || verifyUrl.isBlank()) {
            verifyUrl = DEFAULT_VERIFY_URL;
        }
        if (fromName == null || fromName.isBlank()) {
            fromName = DEFAULT_FROM_NAME;
        }
        if (mailjet == null) {
            mailjet = new Mailjet(null, null);
        }
        if (appsScript == null) {
            appsScript = new AppsScript(null, null);
        }
    }

    /** API key、secret key、寄件人三個都有，才用 Mailjet 寄信 */
    public boolean mailjetEnabled() {
        return mailjet.configured() && hasText(from);
    }

    /** 網址和密語都有，才用 Apps Script 寄信 */
    public boolean appsScriptEnabled() {
        return appsScript.configured();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * @param apiKey    Mailjet 的 API Key（公開的那組）
     * @param secretKey Mailjet 的 Secret Key，不能 commit、不能印在 log
     */
    public record Mailjet(String apiKey, String secretKey) {

        boolean configured() {
            return hasText(apiKey) && hasText(secretKey);
        }

        /** 不印 secret key */
        @Override
        public String toString() {
            return "Mailjet[apiKey=" + (configured() ? apiKey : "（未設定）") + "]";
        }
    }

    /**
     * @param url    部署後拿到的網頁應用程式網址（https://script.google.com/macros/s/…/exec）
     * @param secret 跟 Apps Script 指令碼屬性 SECRET 一樣的密語；沒有密語，知道網址的人就能用你的 Gmail 寄信
     */
    public record AppsScript(String url, String secret) {

        boolean configured() {
            return hasText(url) && hasText(secret);
        }

        /** 不印網址和密語 */
        @Override
        public String toString() {
            return "AppsScript[" + (configured() ? "已設定" : "未設定") + "]";
        }
    }
}
