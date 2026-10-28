package com.ezstock.controller;

import com.ezstock.dto.CompareRequest;
import com.ezstock.dto.CompareResponse;
import com.ezstock.service.BacktestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backtests")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * 標的比較。用 POST 是因為條件比較多，放在 JSON 裡比塞在網址上清楚，
     * 之後條件回測的規則也會是巢狀的 JSON。
     */
    @PostMapping("/compare")
    public CompareResponse compare(@Valid @RequestBody CompareRequest request) {
        return backtestService.compare(request);
    }
}
