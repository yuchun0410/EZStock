package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.WatchlistItemResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.model.StockType;
import com.ezstock.service.WatchlistService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
 * 假裝已經通過 JWT 驗證（會員 id=7），確認 Controller 一律用 JWT 裡的會員 id。
 */
class WatchlistControllerTest {

    private static final List<WatchlistItemResponse> ITEMS = List.of(
            new WatchlistItemResponse("2330", "台積電", StockType.STOCK, LocalDateTime.of(2026, 10, 29, 16, 0)));

    private WatchlistService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WatchlistService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WatchlistController(service))
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
    @DisplayName("查詢、加入、移除：會員 id 來自 JWT")
    void crud() throws Exception {
        when(service.list(7L)).thenReturn(ITEMS);
        when(service.add(7L, "2330")).thenReturn(ITEMS);
        when(service.remove(7L, "2330")).thenReturn(List.of());

        mockMvc.perform(get("/api/members/me/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("台積電"));
        mockMvc.perform(put("/api/members/me/watchlist/2330"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stockId").value("2330"));
        mockMvc.perform(delete("/api/members/me/watchlist/2330"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("匯入：沒給 stockIds 回 400")
    void importValidation() throws Exception {
        when(service.importItems(eq(7L), any())).thenReturn(ITEMS);

        mockMvc.perform(post("/api/members/me/watchlist/import").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockIds\":[\"2330\",\"0050\"]}"))
                .andExpect(status().isOk());
        verify(service).importItems(7L, List.of("2330", "0050"));

        mockMvc.perform(post("/api/members/me/watchlist/import").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).importItems(eq(7L), eq(List.of()));
    }
}
