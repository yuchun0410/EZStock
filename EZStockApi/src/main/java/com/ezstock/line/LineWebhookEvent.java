package com.ezstock.line;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LINE webhook 送來的內容，只取用得到的欄位。
 * 例：{"events":[{"type":"message","replyToken":"...","source":{"type":"user","userId":"U..."},
 *                "message":{"type":"text","text":"2330"}}]}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineWebhookEvent(String type, String replyToken, Source source, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(List<LineWebhookEvent> events) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(String type, String userId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String type, String text) {
    }

    public boolean isTextMessage() {
        return "message".equals(type) && message != null && "text".equals(message.type()) && message.text() != null;
    }
}
