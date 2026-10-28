package com.ezstock.controller;

import com.ezstock.dto.DividendRecord;
import com.ezstock.service.DividendQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class DividendController {

    private final DividendQueryService dividendQueryService;

    public DividendController(DividendQueryService dividendQueryService) {
        this.dividendQueryService = dividendQueryService;
    }

    /** 最近 10 年的除息紀錄，例如 GET /api/stocks/2330/dividends */
    @GetMapping("/{stockId}/dividends")
    public List<DividendRecord> dividends(@PathVariable String stockId) {
        return dividendQueryService.getDividends(stockId);
    }
}
