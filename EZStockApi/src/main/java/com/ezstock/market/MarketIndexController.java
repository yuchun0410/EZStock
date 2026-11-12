package com.ezstock.market;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 首頁市場概況（不需要登入） */
@RestController
@RequestMapping("/api/market")
public class MarketIndexController {

    private final MarketIndexService marketIndexService;
    private final InstitutionalService institutionalService;

    public MarketIndexController(MarketIndexService marketIndexService, InstitutionalService institutionalService) {
        this.marketIndexService = marketIndexService;
        this.institutionalService = institutionalService;
    }

    /** 加權指數、道瓊、S&P 500、那斯達克，例如 GET /api/market/indices */
    @GetMapping("/indices")
    public List<IndexQuote> indices() {
        return marketIndexService.all();
    }

    /** 最近一個交易日的三大法人買賣金額，例如 GET /api/market/institutional */
    @GetMapping("/institutional")
    public InstitutionalSummary institutional() {
        return institutionalService.latest();
    }
}
