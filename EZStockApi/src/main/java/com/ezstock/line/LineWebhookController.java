package com.ezstock.line;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * LINE 的 webhook：使用者傳訊息給 Bot，LINE 會 POST 到這裡。
 * 一定要先驗簽章、再解析 JSON：簽章是對「原始的 body」算的，所以用 String 接收，不讓 Spring 先轉成物件。
 * 處理失敗也回 200，不然 LINE 會一直重送；錯誤寫在 log。
 */
@RestController
public class LineWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookController.class);

    private final LineSignatureVerifier verifier;
    private final LineCommandHandler commandHandler;
    private final LineMessagingClient messagingClient;
    private final JsonMapper jsonMapper;

    public LineWebhookController(LineSignatureVerifier verifier, LineCommandHandler commandHandler,
                                 LineMessagingClient messagingClient, JsonMapper jsonMapper) {
        this.verifier = verifier;
        this.commandHandler = commandHandler;
        this.messagingClient = messagingClient;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/api/line/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String body,
                                        @RequestHeader(name = "X-Line-Signature", required = false) String signature) {
        if (!verifier.isValid(body.getBytes(StandardCharsets.UTF_8), signature)) {
            log.warn("[LINE] webhook 簽章不符，拒絕（可能是偽造的請求，或 channel secret 設錯）");
            return ResponseEntity.status(401).build();
        }

        LineWebhookEvent.Body payload = jsonMapper.readValue(body, LineWebhookEvent.Body.class);
        if (payload.events() == null || payload.events().isEmpty()) {
            // LINE Developers 後台按「Verify」時，會送一個沒有事件的請求來測試網址
            log.info("[LINE] 收到 webhook 驗證請求");
            return ResponseEntity.ok().build();
        }

        for (LineWebhookEvent event : payload.events()) {
            if (!event.isTextMessage() || event.source() == null) {
                log.debug("[LINE] 略過非文字訊息的事件 type={}", event.type());
                continue;
            }
            try {
                messagingClient.reply(event.replyToken(),
                        commandHandler.handle(event.source().userId(), event.message().text()));
            } catch (Exception e) {
                log.error("[LINE] 處理訊息失敗：{}", e.getMessage(), e);
            }
        }
        return ResponseEntity.ok().build();
    }
}
