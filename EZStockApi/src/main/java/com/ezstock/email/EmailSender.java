package com.ezstock.email;

/**
 * 寄信。跟 MarketDataSource 一樣抽成介面，要換寄信服務只要新增實作，呼叫的地方都不用改。
 *
 * 注意：Render 免費方案擋掉寄信用的 SMTP port（25、465、587），正式環境要用寄信服務的 HTTP API。
 * 還沒接寄信服務時，用 {@link LoggingEmailSender}：不真的寄出，只把信的內容寫進 log。
 */
public interface EmailSender {

    /**
     * @param to      收件人
     * @param subject 主旨
     * @param text    純文字內容
     */
    void send(String to, String subject, String text);
}
