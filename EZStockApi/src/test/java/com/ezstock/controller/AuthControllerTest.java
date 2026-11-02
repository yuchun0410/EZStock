package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.MemberResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.exception.UnauthorizedException;
import com.ezstock.security.AccessToken;
import com.ezstock.security.JwtProperties;
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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        RefreshTokenCookies cookies = new RefreshTokenCookies(new JwtProperties(null, null, null, null, false));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookies))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("登入成功：access token 在 body，refresh token 只在 HttpOnly、SameSite=Strict 的 cookie")
    void login() throws Exception {
        when(authService.login(any())).thenReturn(result());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"secret-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.member.email").value("a@example.com"))
                .andExpect(content().string(not(containsString("refresh-value"))))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("ezstock_refresh=refresh-value"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=604800"))));
    }

    @Test
    @DisplayName("註冊：回 201；欄位不合法回 400，不會呼叫 service")
    void register() throws Exception {
        when(authService.register(any())).thenReturn(result());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"secret-123\",\"displayName\":\"小明\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Email 格式不正確")));
        verify(authService).register(any());
    }

    @Test
    @DisplayName("登入失敗：401")
    void loginFailed() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("Email 或密碼錯誤"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email 或密碼錯誤"));
    }

    @Test
    @DisplayName("換 token：從 cookie 讀 refresh token，回新的 access token 與新的 cookie")
    void refresh() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(result());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookies.NAME, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ezstock_refresh=refresh-value")));
    }

    @Test
    @DisplayName("換 token 失敗：401，並刪掉 cookie")
    void refreshFailed() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("登入已過期，請重新登入"));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登入已過期，請重新登入"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("登出：作廢 refresh token、把 access token 交給 service 放黑名單、刪掉 cookie")
    void logout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(RefreshTokenCookies.NAME, "refresh-value"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-value"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout("refresh-value", "jwt-value");
        verify(authService, never()).refresh(any());
    }

    private AuthResult result() {
        MemberResponse member = new MemberResponse(1L, "a@example.com", "小明", null, true, false,
                LocalDateTime.of(2026, 10, 29, 14, 0));
        return new AuthResult(new AccessToken("jwt-value", "jti", Instant.now().plusSeconds(900)),
                "refresh-value", member);
    }
}
