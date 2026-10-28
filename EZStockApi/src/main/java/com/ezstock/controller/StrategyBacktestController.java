package com.ezstock.controller;

import com.ezstock.dto.ConditionTypeInfo;
import com.ezstock.dto.StrategyRequest;
import com.ezstock.dto.StrategyResponse;
import com.ezstock.service.StrategyBacktestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/backtests")
public class StrategyBacktestController {

    private final StrategyBacktestService strategyBacktestService;

    public StrategyBacktestController(StrategyBacktestService strategyBacktestService) {
        this.strategyBacktestService = strategyBacktestService;
    }

    /** 可以選的條件種類，前端用來產生下拉選單，例如 GET /api/backtests/conditions */
    @GetMapping("/conditions")
    public List<ConditionTypeInfo> conditions() {
        return strategyBacktestService.conditionTypes();
    }

    /** 條件回測 */
    @PostMapping("/strategy")
    public StrategyResponse run(@Valid @RequestBody StrategyRequest request) {
        return strategyBacktestService.run(request);
    }
}
