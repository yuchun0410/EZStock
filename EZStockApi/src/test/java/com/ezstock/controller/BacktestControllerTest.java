package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.CompareRequest;
import com.ezstock.dto.CompareResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.service.BacktestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BacktestControllerTest {

    private BacktestService backtestService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        backtestService = mock(BacktestService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BacktestController(backtestService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("標的比較：JSON 轉成 CompareRequest 交給 Service")
    void compare() throws Exception {
        LocalDate start = LocalDate.of(2026, 1, 2);
        when(backtestService.compare(any())).thenReturn(new CompareResponse(
                start, LocalDate.of(2026, 10, 28), new BigDecimal("500000"), true, "不計交易成本",
                List.of(), List.of("起始日之後的第一個交易日，用開盤價買進")));

        mockMvc.perform(post("/api/backtests/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockIds":["2330","0050"],"startDate":"2026-01-02","initialCapital":500000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-01-02"))
                .andExpect(jsonPath("$.assumptions[0]").value(containsString("開盤價")));

        verify(backtestService).compare(new CompareRequest(
                List.of("2330", "0050"), start, null, new BigDecimal("500000"), null, null, null));
    }

    @Test
    @DisplayName("驗證沒過回 400，訊息列出每個錯誤，而且不會呼叫 Service")
    void validationError() throws Exception {
        mockMvc.perform(post("/api/backtests/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockIds":[],"initialCapital":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("請至少選擇 1 檔標的")))
                .andExpect(jsonPath("$.message").value(containsString("請選擇起始日")))
                .andExpect(jsonPath("$.message").value(containsString("投入金額至少 1,000 元")));

        verify(backtestService, never()).compare(any());
    }

    @Test
    @DisplayName("日期格式錯誤回 400，不洩漏內部例外訊息")
    void unreadableJson() throws Exception {
        mockMvc.perform(post("/api/backtests/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockIds":["2330"],"startDate":"2026/01/02","initialCapital":500000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("請求內容格式錯誤，日期請用 yyyy-MM-dd"));
    }
}
