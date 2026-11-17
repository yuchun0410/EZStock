package com.ezstock.email;

import com.ezstock.security.LogMasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 不真的寄信，只把信的內容寫進 log（本機開發、測試用）。
 * 本機註冊後，到 logs/ezstock.log 找「[Email]」，就能複製驗證連結來測。
 *
 * 信的內容有驗證連結，等於一次性的密碼，所以正式環境要換成真的寄信服務，不能一直用這個。
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    public LoggingEmailSender() {
        log.warn("[Email] 沒有設定寄信服務，信不會真的寄出，只會寫進 log");
    }

    @Override
    public void send(String to, String subject, String text) {
        log.info("[Email] （沒有真的寄出）收件人 {}，主旨「{}」\n{}", LogMasks.email(to), subject, text);
    }
}
