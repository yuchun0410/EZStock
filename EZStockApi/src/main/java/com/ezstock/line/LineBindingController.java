package com.ezstock.line;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 網站上的 LINE 綁定（要登入；路徑在 /api/members/me 底下，SecurityConfig 已經要求驗證）。
 */
@RestController
@RequestMapping("/api/members/me/line")
public class LineBindingController {

    private final LineBindingService bindingService;
    private final LineProperties properties;

    public LineBindingController(LineBindingService bindingService, LineProperties properties) {
        this.bindingService = bindingService;
        this.properties = properties;
    }

    /**
     * @param available 後端有沒有設定 LINE Bot
     * @param linked    這個會員有沒有綁定
     * @param botId     Bot 的 LINE ID（例如 @878jfbhc），前端用來顯示「加好友」連結
     */
    public record LineStatus(boolean available, boolean linked, String botId) {
    }

    @GetMapping
    public LineStatus status(@AuthenticationPrincipal Jwt jwt) {
        return new LineStatus(properties.enabled(), bindingService.isLinked(memberId(jwt)), properties.botId());
    }

    @PostMapping("/binding-code")
    public LineBindingService.BindingCode createCode(@AuthenticationPrincipal Jwt jwt) {
        return bindingService.createCode(memberId(jwt));
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt jwt) {
        bindingService.unlink(memberId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static long memberId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
