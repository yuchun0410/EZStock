package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.MemberResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.security.AccessToken;
import com.ezstock.security.JwtProperties;
import com.ezstock.security.LineLoginClient;
import com.ezstock.security.LineLoginProperties;
import com.ezstock.security.LineLoginStateStore;
import com.ezstock.security.LineLoginUser;
import com.ezstock.security.RefreshTokenCookies;
import com.ezstock.service.AuthService;
import com.ezstock.service.AuthService.AuthResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LineLoginControllerTest {

    private static final String BODY = "{\"code\":\"the-code\",\"state\":\"state-1\"}";

    private LineLoginStateStore stateStore;
    private LineLoginClient lineLoginClient;
    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stateStore = mock(LineLoginStateStore.class);
        lineLoginClient = mock(LineLoginClient.class);
        authService = mock(AuthService.class);
        JwtProperties jwt = new JwtProperties(null, null, null, null, false);
        LineLoginController controller = new LineLoginController(
                new LineLoginProperties("1234567890", "secret", "http://localhost:5173/login/line/callback"),
                stateStore, lineLoginClient, authService, new RefreshTokenCookies(jwt), jwt);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("產生登入網址：state 放進 HttpOnly cookie，只在 /api/auth/line 送出")
    void authorize() throws Exception {
        when(stateStore.create()).thenReturn(new LineLoginStateStore.Pending("state-1", "nonce-1"));
        when(lineLoginClient.authorizeUrl("state-1", "nonce-1")).thenReturn("https://access.line.me/oauth2/v2.1/authorize?x");

        mockMvc.perform(get("/api/auth/line/authorize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://access.line.me/oauth2/v2.1/authorize?x"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("ezstock_line_state=state-1"),
                        containsString("HttpOnly"),
                        containsString("Path=/api/auth/line"))));
    }

    @Test
    @DisplayName("登入成功：發 token、設定 refresh cookie，並清掉 state cookie")
    void login() throws Exception {
        when(stateStore.consumeNonce("state-1")).thenReturn(Optional.of("nonce-1"));
        LineLoginUser user = new LineLoginUser("U1", "小明", null, null);
        when(lineLoginClient.authenticate("the-code", "nonce-1")).thenReturn(user);
        when(authService.loginWithLine(user)).thenReturn(result());

        mockMvc.perform(post("/api/auth/line").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .cookie(new Cookie(LineLoginController.STATE_COOKIE, "state-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("ezstock_refresh=refresh-value"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("ezstock_line_state=;"))));
    }

    @Test
    @DisplayName("state 跟 cookie 對不上、或沒有 cookie（CSRF）：401，不會拿 code 去換 token")
    void stateMismatch() throws Exception {
        mockMvc.perform(post("/api/auth/line").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .cookie(new Cookie(LineLoginController.STATE_COOKIE, "someone-else")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/line").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(lineLoginClient, never()).authenticate(anyString(), anyString());
    }

    @Test
    @DisplayName("state 過期或已經用過：401")
    void stateExpired() throws Exception {
        when(stateStore.consumeNonce("state-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/line").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .cookie(new Cookie(LineLoginController.STATE_COOKIE, "state-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("LINE 登入逾時或來源不符，請重新登入"));
        verify(authService, never()).loginWithLine(any());
    }

    private AuthResult result() {
        MemberResponse member = new MemberResponse(1L, null, "小明", null, false, false, true,
                LocalDateTime.of(2026, 11, 10, 12, 0));
        return new AuthResult(new AccessToken("jwt-value", "jti", Instant.now().plusSeconds(900)), "refresh-value", member);
    }
}
