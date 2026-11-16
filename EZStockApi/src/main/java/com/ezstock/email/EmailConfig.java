package com.ezstock.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 決定用哪一種寄信方式。目前還沒接寄信服務，一律用 LoggingEmailSender；
 * 之後接上 Brevo、Mailjet 之類的服務時，在這裡依設定切換即可。
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    public EmailSender emailSender() {
        return new LoggingEmailSender();
    }
}
