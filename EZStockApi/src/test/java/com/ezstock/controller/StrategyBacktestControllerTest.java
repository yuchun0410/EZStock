package com.ezstock.controller;

import com.ezstock.backtest.strategy.ConditionType;
import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.ConditionTypeInfo;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.service.StrategyBacktestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyBacktestControllerTest {

    private StrategyBacktestService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(StrategyBacktestService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StrategyBacktestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("條件種類：回傳名稱、單位與範圍")
    void conditions() throws Exception {
        when(service.conditionTypes()).thenReturn(List.of(ConditionTypeInfo.from(ConditionType.CLOSE_BELOW_LOWEST_LOW)));

        mockMvc.perform(get("/api/backtests/conditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLOSE_BELOW_LOWEST_LOW"))
                .andExpect(jsonPath("$[0].label").value("收盤跌破前 N 日最低價"))
                .andExpect(jsonPath("$[0].unit").value("日"))
                .andExpect(jsonPath("$[0].entryAllowed").value(true));
    }

    @Test
    @DisplayName("條件回測：沒有買進條件、條件沒填數值，回 400 並列出錯誤")
    void validation() throws Exception {
        mockMvc.perform(post("/api/backtests/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockId":"2330","startDate":"2024-01-02","initialCapital":500000,
                                 "entryConditions":[],
                                 "exitConditions":[{"type":"STOP_LOSS"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("請至少設定 1 個買進條件")))
                .andExpect(jsonPath("$.message").value(containsString("請輸入條件的數值")));

        verify(service, never()).run(any());
    }

    @Test
    @DisplayName("條件回測：不存在的條件種類回 400")
    void unknownType() throws Exception {
        mockMvc.perform(post("/api/backtests/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockId":"2330","startDate":"2024-01-02","initialCapital":500000,
                                 "entryConditions":[{"type":"KD_GOLDEN_CROSS","value":9}],
                                 "exitConditions":[{"type":"STOP_LOSS","value":5}]}
                                """))
                .andExpect(status().isBadRequest());
    }
}
