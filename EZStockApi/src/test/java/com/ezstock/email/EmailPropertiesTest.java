package com.ezstock.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPropertiesTest {

    @Test
    @DisplayName("沒設定的欄位用預設值；沒有 Mailjet 金鑰就不啟用 Mailjet")
    void defaults() {
        EmailProperties properties = new EmailProperties(null, null, null, null, null);

        assertThat(properties.verifyUrl()).isEqualTo(EmailProperties.DEFAULT_VERIFY_URL);
        assertThat(properties.fromName()).isEqualTo("EZStock");
        assertThat(properties.mailjetEnabled()).isFalse();
    }

    @Test
    @DisplayName("API Key、Secret Key、寄件人三個都有才啟用 Mailjet")
    void mailjetEnabled() {
        var keys = new EmailProperties.Mailjet("public-key", "secret-key");

        assertThat(new EmailProperties(null, "noreply@example.com", null, keys, null).mailjetEnabled()).isTrue();
        assertThat(new EmailProperties(null, "", null, keys, null).mailjetEnabled()).as("沒有寄件人").isFalse();
        assertThat(new EmailProperties(null, "noreply@example.com", null,
                new EmailProperties.Mailjet("public-key", " "), null).mailjetEnabled()).as("沒有 Secret Key").isFalse();
    }

    @Test
    @DisplayName("Apps Script：網址和密語都有才啟用")
    void appsScriptEnabled() {
        assertThat(new EmailProperties(null, null, null, null,
                new EmailProperties.AppsScript("https://script.google.com/macros/s/abc/exec", "secret"))
                .appsScriptEnabled()).isTrue();
        assertThat(new EmailProperties(null, null, null, null,
                new EmailProperties.AppsScript("https://script.google.com/macros/s/abc/exec", ""))
                .appsScriptEnabled()).as("沒有密語").isFalse();
        assertThat(new EmailProperties(null, null, null, null, null).appsScriptEnabled()).isFalse();
    }

    @Test
    @DisplayName("toString 不能印出 Secret Key（設定物件可能被印進 log）")
    void secretNotPrinted() {
        var properties = new EmailProperties(null, "noreply@example.com", null,
                new EmailProperties.Mailjet("public-key", "super-secret"),
                new EmailProperties.AppsScript("https://script.google.com/macros/s/abc/exec", "apps-secret"));

        assertThat(properties.toString()).doesNotContain("super-secret", "apps-secret", "macros/s/abc");
    }
}
