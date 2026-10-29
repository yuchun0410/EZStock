package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.LoginRequest;
import com.ezstock.dto.RegisterRequest;
import com.ezstock.dto.TokenResponse;
import com.ezstock.exception.ApiError;
import com.ezstock.exception.UnauthorizedException;
import com.ezstock.security.RefreshTokenCookies;
import com.ezstock.service.AuthService;
import com.ezstock.service.AuthService.AuthResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * 註冊、登入、換 token、登出。
 * access token 放在回應 body，前端存在記憶體；refresh token 放在 HttpOnly cookie。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String BEARER = "Bearer ";

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withTokens(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(HttpStatus.OK, authService.login(request));
    }

    /**
     * 用 cookie 裡的 refresh token 換新的 access token。
     * 失敗時順便叫瀏覽器刪掉 cookie，前端就知道要重新登入。
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        try {
            return withTokens(HttpStatus.OK, authService.refresh(refreshToken));
        } catch (UnauthorizedException e) {
            log.info("[會員] 換發 token 失敗：{}", e.getMessage());
            ApiError body = new ApiError(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", e.getMessage(),
                    MDC.get(RequestLoggingFilter.TRACE_ID));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .body(body);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String accessToken = authorization != null && authorization.startsWith(BEARER)
                ? authorization.substring(BEARER.length()) : null;
        authService.logout(refreshToken, accessToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> withTokens(HttpStatus status, AuthResult result) {
        long expiresIn = Math.max(0, Duration.between(Instant.now(), result.accessToken().expiresAt()).toSeconds());
        TokenResponse body = new TokenResponse(result.accessToken().value(), "Bearer", expiresIn, result.member());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(body);
    }
}
