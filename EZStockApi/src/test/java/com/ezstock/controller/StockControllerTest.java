package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.datasource.finmind.FinMindApiException;
import com.ezstock.dto.PricePoint;
import com.ezstock.dto.StockDetail;
import com.ezstock.dto.StockSummary;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.StockType;
import com.ezstock.service.StockQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只測 Controller 這一層：網址、參數、JSON 格式、錯誤回應。
 * Service 用 Mockito 假裝，不連資料庫。
 */
class StockControllerTest {

    private StockQueryService stockQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stockQueryService = mock(StockQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StockController(stockQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("搜尋：回傳股票清單，回應標頭帶 traceId")
    void search() throws Exception {
        when(stockQueryService.search("台積"))
                .thenReturn(List.of(new StockSummary("2330", "台積電", StockType.STOCK, "半導體業")));

        mockMvc.perform(get("/api/stocks").param("keyword", "台積"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestLoggingFilter.TRACE_ID_HEADER))
                .andExpect(jsonPath("$[0].stockId").value("2330"))
                .andExpect(jsonPath("$[0].name").value("台積電"))
                .andExpect(jsonPath("$[0].type").value("STOCK"));
    }

    @Test
    @DisplayName("搜尋：沒帶 keyword 回 400")
    void searchWithoutKeyword() throws Exception {
        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("缺少參數 keyword"));
    }

    @Test
    @DisplayName("個股：回傳最新行情，日期是 yyyy-MM-dd 字串")
    void detail() throws Exception {
        when(stockQueryService.getDetail("2330")).thenReturn(new StockDetail(
                "2330", "台積電", StockType.STOCK, "半導體業",
                LocalDate.of(2026, 10, 27), new BigDecimal("1100.00"),
                new BigDecimal("15.00"), new BigDecimal("1.38")));

        mockMvc.perform(get("/api/stocks/2330"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestDate").value("2026-10-27"))
                .andExpect(jsonPath("$.latestClose").value(1100.00))
                .andExpect(jsonPath("$.changePercent").value(1.38));
    }

    @Test
    @DisplayName("個股：查無股票回 404，錯誤格式包含 traceId")
    void detailNotFound() throws Exception {
        when(stockQueryService.getDetail("9999")).thenThrow(new ResourceNotFoundException("查無股票 9999"));

        mockMvc.perform(get("/api/stocks/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("查無股票 9999"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("日線：日期參數轉成 LocalDate 傳給 Service")
    void prices() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(stockQueryService.getPrices("2330", from, to)).thenReturn(List.of(new PricePoint(
                LocalDate.of(2026, 1, 2), new BigDecimal("100"), new BigDecimal("106"),
                new BigDecimal("99.5"), new BigDecimal("105.5"), 1000L)));

        mockMvc.perform(get("/api/stocks/2330/prices").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-01-02"))
                .andExpect(jsonPath("$[0].close").value(105.5));

        verify(stockQueryService).getPrices("2330", from, to);
    }

    @Test
    @DisplayName("日線：日期格式錯誤回 400")
    void pricesWithBadDate() throws Exception {
        mockMvc.perform(get("/api/stocks/2330/prices").param("from", "2026/01/01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("外部資料來源失敗回 502，訊息不洩漏內部細節")
    void upstreamError() throws Exception {
        when(stockQueryService.getPrices(anyString(), any(), any()))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));

        mockMvc.perform(get("/api/stocks/2330/prices"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.message").value("外部資料來源暫時無法使用，請稍後再試"));
    }
}
