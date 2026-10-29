package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.ScreenMatch;
import com.ezstock.dto.ScreenResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.model.StockType;
import com.ezstock.service.ScreenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScreenerControllerTest {

    private ScreenerService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ScreenerService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ScreenerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("條件選股：回傳符合的股票")
    void screen() throws Exception {
        when(service.screen(any())).thenReturn(new ScreenResponse("收盤站上 20 日線（月線）", 2,
                List.of(new ScreenMatch("2330", "台積電", StockType.STOCK, "半導體業", LocalDate.of(2026, 10, 28),
                        new BigDecimal("1500"), new BigDecimal("10"), new BigDecimal("0.67"))),
                List.of()));

        mockMvc.perform(post("/api/screener").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditions\":[{\"type\":\"CLOSE_ABOVE_MOVING_AVERAGE\",\"value\":20}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(2))
                .andExpect(jsonPath("$.matches[0].stockId").value("2330"))
                .andExpect(jsonPath("$.matches[0].changePercent").value(0.67));
    }

    @Test
    @DisplayName("沒有條件：回 400，不會呼叫 service")
    void noConditions() throws Exception {
        mockMvc.perform(post("/api/screener").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditions\":[]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).screen(any());
    }
}
