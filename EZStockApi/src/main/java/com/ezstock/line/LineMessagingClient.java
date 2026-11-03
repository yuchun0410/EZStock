package com.ezstock.line;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * 呼叫 LINE Messaging API：
 * - reply：回覆使用者剛傳的訊息，要用 webhook 給的 replyToken（只能用一次、一分鐘內有效），不算推播額度
 * - push：主動傳訊息（例如每日回報），會用掉每月的免費推播額度
 * 一則訊息最多 5000 字、一次最多 5 則。log 不印 token，使用者 id 只印最後 6 碼。
 */
@Component
public class LineMessagingClient {

    private static final Logger log = LoggerFactory.getLogger(LineMessagingClient.class);
    private static final int MAX_TEXT = 5000;
    private static final int MAX_MESSAGES = 5;

    private final RestClient restClient;
    private final LineProperties properties;

    public LineMessagingClient(RestClient lineRestClient, LineProperties properties) {
        this.restClient = lineRestClient;
        this.properties = properties;
    }

    public void reply(String replyToken, List<String> texts) {
        send("/v2/bot/message/reply", Map.of("replyToken", replyToken, "messages", messages(texts)), "回覆");
    }

    /** @return 是否成功（推播失敗不丟例外，讓排程繼續處理下一個人） */
    public boolean push(String userId, List<String> texts) {
        try {
            send("/v2/bot/message/push", Map.of("to", userId, "messages", messages(texts)), "推播給 " + mask(userId));
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.error("[LINE] 推播給 {} 失敗：{}", mask(userId), e.getMessage());
            return false;
        }
    }

    private void send(String path, Map<String, Object> body, String action) {
        if (!properties.enabled()) {
            throw new IllegalStateException("LINE 沒有設定，無法" + action);
        }
        restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.info("[LINE] {}成功，{} 則訊息", action, ((List<?>) body.get("messages")).size());
    }

    private static List<Map<String, String>> messages(List<String> texts) {
        return texts.stream()
                .limit(MAX_MESSAGES)
                .map(text -> Map.of("type", "text", "text", text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text))
                .toList();
    }

    /** LINE 使用者 id 是個資，log 只留最後 6 碼 */
    public static String mask(String userId) {
        if (userId == null || userId.length() <= 6) {
            return "******";
        }
        return "…" + userId.substring(userId.length() - 6);
    }
}
