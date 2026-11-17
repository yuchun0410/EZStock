package com.ezstock.email;

import com.ezstock.security.LogMasks;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * 透過 Google Apps Script 的網頁應用程式，用部署者自己的 Gmail 寄信。
 *
 * 為什麼用這個：Render 免費方案擋掉 SMTP；寄信服務（Brevo、Mailjet）對沒有自有網域、用 Gmail 當寄件人的新帳號
 * 會暫停寄信。Apps Script 走 HTTPS，信從真的 Gmail 寄出，免費每天約 100 封，作品集網站夠用。
 *
 * 呼叫方式：POST JSON { secret, to, subject, text }，回 { ok: true } 或 { ok: false, error }。
 * Apps Script 會先回 302 轉址到真正的結果，所以 HTTP client 要能跟著轉址（見 {@link EmailHttp}）。
 */
public class AppsScriptEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(AppsScriptEmailSender.class);

    private final EmailProperties.AppsScript appsScript;
    private final RestClient restClient;

    public AppsScriptEmailSender(EmailProperties properties) {
        this(properties, RestClient.builder().requestFactory(EmailHttp.requestFactory()));
    }

    /** 測試時傳入綁了 MockRestServiceServer 的 builder */
    AppsScriptEmailSender(EmailProperties properties, RestClient.Builder builder) {
        this.appsScript = properties.appsScript();
        this.restClient = builder.build();
        log.info("[Email] 使用 Google Apps Script 寄信");
    }

    @Override
    public void send(String to, String subject, String text) {
        Result result;
        try {
            result = restClient.post()
                    .uri(appsScript.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("secret", appsScript.secret(), "to", to, "subject", subject, "text", text))
                    .retrieve()
                    .body(Result.class);
        } catch (RestClientResponseException e) {
            log.warn("[Email] Apps Script 寄信失敗 HTTP {}", e.getStatusCode().value());
            throw new IllegalStateException("Apps Script 寄信失敗 HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // 例如 Apps Script 出錯時回的是 HTML 錯誤頁，不是 JSON
            log.error("[Email] 無法呼叫 Apps Script：{}", e.getMessage());
            throw new IllegalStateException("無法呼叫 Apps Script", e);
        }
        if (result == null || !result.ok()) {
            String error = result == null ? "沒有回應內容" : result.error();
            log.warn("[Email] Apps Script 回應失敗：{}", error);
            throw new IllegalStateException("Apps Script 寄信失敗：" + error);
        }
        log.info("[Email] Apps Script 已寄出 收件人 {}，主旨「{}」", LogMasks.email(to), subject);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(boolean ok, String error) {
    }
}
