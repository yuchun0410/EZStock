package com.ezstock.datasource.finmind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * FinMind API 回傳的 JSON 格式。
 * 只在 finmind 套件內使用，其他地方一律轉成 StockInfo、PriceBar、CashDividend。
 */
final class FinMindDto {

    private FinMindDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response<T>(String msg, Integer status, List<T> data) {
    }

    /** TaiwanStockInfo */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockInfoRow(
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("stock_name") String stockName,
            @JsonProperty("industry_category") String industryCategory,
            @JsonProperty("type") String type) {
    }

    /** TaiwanStockPrice（max / min 是最高價 / 最低價） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("Trading_Volume") Long tradingVolume,
            @JsonProperty("open") BigDecimal open,
            @JsonProperty("max") BigDecimal max,
            @JsonProperty("min") BigDecimal min,
            @JsonProperty("close") BigDecimal close) {
    }

    /** TaiwanStockDividendResult（kind 是「息」「權」或「權息」） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DividendResultRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("stock_and_cache_dividend") BigDecimal value,
            @JsonProperty("stock_or_cache_dividend") String kind) {
    }
}
