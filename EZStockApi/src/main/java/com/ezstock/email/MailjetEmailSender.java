package com.ezstock.email;

import com.ezstock.security.LogMasks;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * 用 Mailjet 的 Send API v3.1 寄信（走 HTTPS，不會被 Render 免費方案擋掉 SMTP 影響）。
 *
 * POST https://api.mailjet.com/v3.1/send，用 Basic Auth（API Key:Secret Key），
 * 內容是一個 Messages 陣列；成功時每封信回傳 "Status": "success"。
 */
public class MailjetEmailSender implements EmailSender {

    static final String API_BASE_URL = "https://api.mailjet.com";

    private static final Logger log = LoggerFactory.getLogger(MailjetEmailSender.class);

    private final EmailProperties properties;
    private final RestClient restClient;

    public MailjetEmailSender(EmailProperties properties) {
        this(properties, defaultBuilder());
    }

    /** 測試時傳入綁了 MockRestServiceServer 的 builder */
    MailjetEmailSender(EmailProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        EmailProperties.Mailjet mailjet = properties.mailjet();
        this.restClient = builder
                .defaultHeaders(headers -> headers.setBasicAuth(mailjet.apiKey(), mailjet.secretKey()))
                .build();
        log.info("[Email] 使用 Mailjet 寄信，寄件人 {}", LogMasks.email(properties.from()));
    }

    static RestClient.Builder defaultBuilder() {
        return RestClient.builder().baseUrl(API_BASE_URL).requestFactory(EmailHttp.requestFactory());
    }

    @Override
    public void send(String to, String subject, String text) {
        Map<String, Object> message = Map.of(
                "From", Map.of("Email", properties.from(), "Name", properties.fromName()),
                "To", List.of(Map.of("Email", to)),
                "Subject", subject,
                "TextPart", text);
        SendResponse response;
        try {
            response = restClient.post()
                    .uri("/v3.1/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("Messages", List.of(message)))
                    .retrieve()
                    .body(SendResponse.class);
        } catch (RestClientResponseException e) {
            // 401：金鑰錯；400：寄件人沒驗證或格式錯。Mailjet 的錯誤說明不含金鑰，可以記
            log.warn("[Email] Mailjet 寄信失敗 HTTP {}：{}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("Mailjet 寄信失敗 HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            log.error("[Email] 無法連線 Mailjet：{}", e.getMessage());
            throw new IllegalStateException("無法連線 Mailjet", e);
        }

        String status = response == null || response.messages() == null || response.messages().isEmpty()
                ? null : response.messages().getFirst().status();
        if (!"success".equals(status)) {
            log.warn("[Email] Mailjet 回應不是成功：{}", status);
            throw new IllegalStateException("Mailjet 寄信失敗：" + status);
        }
        log.info("[Email] Mailjet 已寄出 收件人 {}，主旨「{}」", LogMasks.email(to), subject);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SendResponse(@JsonProperty("Messages") List<MessageResult> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageResult(@JsonProperty("Status") String status) {
    }
}
