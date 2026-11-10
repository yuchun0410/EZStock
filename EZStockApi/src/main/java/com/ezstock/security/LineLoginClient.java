package com.ezstock.security;

import com.ezstock.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * 呼叫 LINE Login 的 API（OAuth 2.0 + OpenID Connect）：
 * 1. 組授權網址，讓瀏覽器導到 LINE 登入
 * 2. 用 LINE 帶回來的 code 換 ID Token（要帶 channel secret，所以一定要在後端做，不能放前端）
 * 3. 把 ID Token 交給 LINE 的 verify API 驗證（簽章、發行者、對象、到期時間、nonce），拿到使用者資料
 */
@Component
public class LineLoginClient {

    static final String AUTHORIZE_URL = "https://access.line.me/oauth2/v2.1/authorize";
    private static final String API_BASE_URL = "https://api.line.me";
    /** openid：拿 ID Token；profile：名稱和頭像；email：有申請權限、使用者也同意才會真的給 */
    private static final String SCOPE = "profile openid email";

    private static final Logger log = LoggerFactory.getLogger(LineLoginClient.class);

    private final LineLoginProperties properties;
    private final RestClient restClient;

    /** 有兩個建構子，標 @Autowired 告訴 Spring 用這個（另一個給測試塞假的 RestClient） */
    @Autowired
    public LineLoginClient(LineLoginProperties properties) {
        this(properties, defaultBuilder());
    }

    /** 測試時傳入綁了 MockRestServiceServer 的 builder */
    LineLoginClient(LineLoginProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    /** 正式環境用：設定連線逾時，LINE 沒回應時不要讓請求一直卡著 */
    static RestClient.Builder defaultBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(API_BASE_URL).requestFactory(requestFactory);
    }

    /**
     * bot_prompt=aggressive：登入時順便跳出「加 EZStock Bot 好友」的選項。
     * state、nonce 都是只有英數和 -_ 的隨機字串。
     */
    public String authorizeUrl(String state, String nonce) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.channelId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", state)
                .queryParam("scope", SCOPE)
                .queryParam("nonce", nonce)
                .queryParam("bot_prompt", "aggressive")
                .encode()
                .toUriString();
    }

    public LineLoginUser authenticate(String code, String nonce) {
        TokenResponse token = post("/oauth2/v2.1/token", form(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", properties.redirectUri(),
                "client_id", properties.channelId(),
                "client_secret", properties.channelSecret()), TokenResponse.class, "換 token");
        if (token == null || token.idToken() == null) {
            log.warn("[LINE 登入] 回應沒有 ID Token（scope 沒有包含 openid？）");
            throw new UnauthorizedException("LINE 登入失敗，請再試一次");
        }

        VerifyResponse verified = post("/oauth2/v2.1/verify", form(
                "id_token", token.idToken(),
                "client_id", properties.channelId(),
                "nonce", nonce), VerifyResponse.class, "驗證 ID Token");
        if (verified == null || verified.sub() == null) {
            throw new UnauthorizedException("LINE 登入失敗，請再試一次");
        }
        log.info("[LINE 登入] 驗證成功 使用者 {}，{}提供 Email", LogMasks.lineUserId(verified.sub()),
                verified.email() == null ? "沒有" : "有");
        return new LineLoginUser(verified.sub(), verified.name(), verified.picture(), verified.email());
    }

    /** code 過期、被用過、channel secret 錯都會回 400；log 只記狀態碼和 LINE 的錯誤說明，不記 code 和 token */
    private <T> T post(String path, MultiValueMap<String, String> body, Class<T> type, String action) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            log.warn("[LINE 登入] {}失敗 HTTP {}：{}", action, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new UnauthorizedException("LINE 登入失敗，請再試一次");
        } catch (RestClientException e) {
            log.error("[LINE 登入] {}時無法連線 LINE：{}", action, e.getMessage());
            throw new UnauthorizedException("暫時無法連線 LINE，請稍後再試");
        }
    }

    private static MultiValueMap<String, String> form(String... keyValues) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            form.add(keyValues[i], keyValues[i + 1]);
        }
        return form;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("id_token") String idToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerifyResponse(String sub, String name, String picture, String email) {
    }
}
