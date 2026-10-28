package com.ezstock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 對應 application.properties 的 ezstock.import.*
 *
 * @param stockIds     要匯入價格與股利的股票代號
 * @param years        匯入最近幾年的資料
 * @param runOnStartup 啟動時是否自動匯入一次
 */
@ConfigurationProperties(prefix = "ezstock.import")
public record ImportProperties(List<String> stockIds, Integer years, Boolean runOnStartup) {

    public ImportProperties {
        stockIds = stockIds == null ? List.of() : List.copyOf(stockIds);
        years = years == null ? 5 : years;
        runOnStartup = runOnStartup != null && runOnStartup;
    }
}
