package com.ezstock.controller;

import com.ezstock.dto.PricePoint;
import com.ezstock.dto.StockDetail;
import com.ezstock.dto.StockSummary;
import com.ezstock.service.StockQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票查詢 API（不需要登入）。
 * Controller 只負責接參數、呼叫 Service、回傳結果；
 * 請求 log 由 RequestLoggingFilter 統一記錄，錯誤由 GlobalExceptionHandler 統一處理。
 */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockQueryService stockQueryService;

    public StockController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    /** 搜尋股票，例如 GET /api/stocks?keyword=台積 */
    @GetMapping
    public List<StockSummary> search(@RequestParam String keyword) {
        return stockQueryService.search(keyword);
    }

    /** 個股資料與最新行情，例如 GET /api/stocks/2330 */
    @GetMapping("/{stockId}")
    public StockDetail detail(@PathVariable String stockId) {
        return stockQueryService.getDetail(stockId);
    }

    /** 歷史日線，例如 GET /api/stocks/2330/prices?from=2026-01-01&to=2026-06-30 */
    @GetMapping("/{stockId}/prices")
    public List<PricePoint> prices(
            @PathVariable String stockId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stockQueryService.getPrices(stockId, from, to);
    }
}
