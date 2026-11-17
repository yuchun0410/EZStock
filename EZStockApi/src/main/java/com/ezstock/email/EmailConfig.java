package com.ezstock.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 決定用哪一種寄信方式，優先順序：
 * 1. Google Apps Script（設定了網址和密語）：用自己的 Gmail 寄出
 * 2. Mailjet（設定了金鑰和寄件人）
 * 3. 都沒設定（本機開發、測試）：LoggingEmailSender 寫進 log
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    public EmailSender emailSender(EmailProperties properties) {
        if (properties.appsScriptEnabled()) {
            return new AppsScriptEmailSender(properties);
        }
        if (properties.mailjetEnabled()) {
            return new MailjetEmailSender(properties);
        }
        return new LoggingEmailSender();
    }
}
