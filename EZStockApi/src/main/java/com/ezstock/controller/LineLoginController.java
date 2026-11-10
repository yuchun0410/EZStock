package com.ezstock.controller;

import com.ezstock.dto.LineAuthorizeResponse;
import com.ezstock.dto.LineLoginRequest;
import com.ezstock.dto.TokenResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.UnauthorizedException;
import com.ezstock.security.JwtProperties;
import com.ezstock.security.LineLoginClient;
import com.ezstock.security.LineLoginProperties;
import com.ezstock.security.LineLoginStateStore;
import com.ezstock.security.LineLoginUser;
import com.ezstock.security.RefreshTokenCookies;
import com.ezstock.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * LINE 登入（OAuth 2.0 授權碼流程）：
 * 1. GET  /api/auth/line/authorize：產生 state、nonce，state 放進 HttpOnly cookie，回傳 LINE 登入網址
 * 2. 瀏覽器導到 LINE，使用者同意後，LINE 導回前端 /login/line/callback?code=…&state=…
 * 3. POST /api/auth/line：前端把 code、state 交給後端；後端比對 cookie 和 Redis 裡的 state，再用 code 換 ID Token、驗證，
 *    最後跟其他登入方式一樣發我們自己的 JWT
 */
@RestController
@RequestMapping("/api/auth/line")
public class LineLoginController {

    static final String STATE_COOKIE = "ezstock_line_state";
    private static final String STATE_COOKIE_PATH = "/api/auth/line";
    private static final String STATE_MISMATCH = "LINE 登入逾時或來源不符，請重新登入";

    private static final Logger log = LoggerFactory.getLogger(LineLoginController.class);

    private final LineLoginProperties properties;
    private final LineLoginStateStore stateStore;
    private final LineLoginClient lineLoginClient;
    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final JwtProperties jwtProperties;

    public LineLoginController(LineLoginProperties properties, LineLoginStateStore stateStore,
                               LineLoginClient lineLoginClient, AuthService authService,
                               RefreshTokenCookies refreshTokenCookies, JwtProperties jwtProperties) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.lineLoginClient = lineLoginClient;
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
        this.jwtProperties = jwtProperties;
    }

    @GetMapping("/authorize")
    public ResponseEntity<LineAuthorizeResponse> authorize() {
        if (!properties.enabled()) {
            throw new BadRequestException("目前沒有開放 LINE 登入");
        }
        LineLoginStateStore.Pending pending = stateStore.create();
        log.info("[LINE 登入] 產生登入網址");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, stateCookie(pending.state(), LineLoginStateStore.TTL).toString())
                .body(new LineAuthorizeResponse(lineLoginClient.authorizeUrl(pending.state(), pending.nonce())));
    }

    @PostMapping
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LineLoginRequest request,
                                               @CookieValue(name = STATE_COOKIE, required = false) String cookieState) {
        if (!properties.enabled()) {
            throw new BadRequestException("目前沒有開放 LINE 登入");
        }
        // 網址上的 state 要跟這個瀏覽器 cookie 裡的一樣（用固定時間比較），而且 Redis 裡有、只能用一次
        if (cookieState == null || !MessageDigest.isEqual(cookieState.getBytes(StandardCharsets.UTF_8),
                request.state().getBytes(StandardCharsets.UTF_8))) {
            log.warn("[LINE 登入] state 跟 cookie 對不上（可能是 CSRF、或 cookie 過期／被清掉）");
            throw new UnauthorizedException(STATE_MISMATCH);
        }
        String nonce = stateStore.consumeNonce(request.state()).orElseThrow(() -> {
            log.warn("[LINE 登入] state 已過期或已使用過");
            return new UnauthorizedException(STATE_MISMATCH);
        });

        LineLoginUser lineUser = lineLoginClient.authenticate(request.code(), nonce);
        return AuthController.tokenResponse(HttpStatus.OK, authService.loginWithLine(lineUser), refreshTokenCookies,
                stateCookie("", Duration.ZERO).toString());
    }

    /**
     * SameSite=Lax：從 LINE 導回來是跨站的「頁面跳轉」，Lax 會帶 cookie、Strict 不一定；
     * 這個 cookie 只是用來比對 state，不能拿來做任何操作，Lax 已經足夠。
     */
    private ResponseCookie stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Lax")
                .path(STATE_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
