package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.DividendRecord;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.service.DividendQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DividendControllerTest {

    private DividendQueryService dividendQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dividendQueryService = mock(DividendQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DividendController(dividendQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("股利：回傳除息紀錄，算不出殖利率的欄位是 null")
    void dividends() throws Exception {
        when(dividendQueryService.getDividends("2330")).thenReturn(List.of(
                new DividendRecord(LocalDate.of(2026, 6, 16), new BigDecimal("6.0000"),
                        new BigDecimal("1200.00"), new BigDecimal("0.50")),
                new DividendRecord(LocalDate.of(2021, 11, 1), new BigDecimal("2.7500"), null, null)));

        mockMvc.perform(get("/api/stocks/2330/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exDate").value("2026-06-16"))
                .andExpect(jsonPath("$[0].cashDividend").value(6.0))
                .andExpect(jsonPath("$[0].yieldPercent").value(0.5))
                .andExpect(jsonPath("$[1].yieldPercent").doesNotExist());
    }

    @Test
    @DisplayName("股利：查無股票回 404")
    void notFound() throws Exception {
        when(dividendQueryService.getDividends("9999")).thenThrow(new ResourceNotFoundException("查無股票 9999"));

        mockMvc.perform(get("/api/stocks/9999/dividends"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("查無股票 9999"));
    }
}
