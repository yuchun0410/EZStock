package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.service.AssetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 假裝已經通過 JWT 驗證（會員 id=7）。
 */
class AssetControllerTest {

    private static final AssetSummaryResponse SUMMARY = new AssetSummaryResponse(
            new BigDecimal("300000"), new BigDecimal("200500"), new BigDecimal("500500"),
            new BigDecimal("150000"), new BigDecimal("350500"), new BigDecimal("201000"),
            new BigDecimal("-500"), new BigDecimal("-0.25"), List.of(), List.of(), List.of(), null, null);

    private AssetService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AssetService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AssetController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("7").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("查詢與修改：會員 id 來自 JWT，回傳最新的儀表板")
    void endpoints() throws Exception {
        when(service.summary(7L)).thenReturn(SUMMARY);
        when(service.createAccount(eq(7L), any())).thenReturn(SUMMARY);
        when(service.saveHolding(eq(7L), eq("2330"), any())).thenReturn(SUMMARY);
        when(service.deleteAccount(7L, 3L)).thenReturn(SUMMARY);

        mockMvc.perform(get("/api/members/me/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netWorth").value(350500));
        mockMvc.perform(post("/api/members/me/assets/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CASH\",\"name\":\"活存\",\"balance\":300000}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/members/me/assets/holdings/2330").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shares\":1000,\"averageCost\":950.5}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/members/me/assets/accounts/3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("欄位不合法：400，不會呼叫 service")
    void validation() throws Exception {
        mockMvc.perform(post("/api/members/me/assets/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CASH\",\"name\":\"\",\"balance\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("金額不能是負數")));
        mockMvc.perform(put("/api/members/me/assets/holdings/2330").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shares\":0,\"averageCost\":0}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).createAccount(anyLong(), any());
        verify(service, never()).saveHolding(anyLong(), any(), any());
    }
}
