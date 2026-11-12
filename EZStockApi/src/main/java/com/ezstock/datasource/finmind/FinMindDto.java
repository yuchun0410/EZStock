package com.ezstock.datasource.finmind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * FinMind API 回傳的 JSON 格式。
 * 只在 finmind 套件內使用，其他地方一律轉成 StockInfo、PriceBar、CashDividend、ShareSplit。
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

    /** TaiwanStockSplitPrice（台股分割後參考價） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SplitPriceRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("type") String type,
            @JsonProperty("before_price") BigDecimal beforePrice,
            @JsonProperty("after_price") BigDecimal afterPrice) {
    }

    /** TaiwanStockNews（相關新聞，一次只能查一天） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewsRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("title") String title,
            @JsonProperty("source") String source,
            @JsonProperty("link") String link) {
    }

    /** TaiwanStockParValueChange（變更面額恢復買賣參考價格） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParValueChangeRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("before_close") BigDecimal beforeClose,
            @JsonProperty("after_ref_close") BigDecimal afterRefClose) {
    }

    /** USStockInfo（美股清單；同一檔會有好幾個日期的快照，最新日期的才是目前掛牌的） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsStockInfoRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("stock_name") String stockName,
            @JsonProperty("Subsector") String subsector,
            @JsonProperty("MarketCap") BigDecimal marketCap) {
    }

    /** USStockPrice（Close 已還原分割；Adj_Close 再還原股利） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsPriceRow(
            @JsonProperty("date") String date,
            @JsonProperty("stock_id") String stockId,
            @JsonProperty("Open") BigDecimal open,
            @JsonProperty("High") BigDecimal high,
            @JsonProperty("Low") BigDecimal low,
            @JsonProperty("Close") BigDecimal close,
            @JsonProperty("Adj_Close") BigDecimal adjClose,
            @JsonProperty("Volume") BigDecimal volume) {
    }

    /** TaiwanExchangeRate（臺灣銀行牌告匯率；沒有報價的日子會是 -99） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExchangeRateRow(
            @JsonProperty("date") String date,
            @JsonProperty("currency") String currency,
            @JsonProperty("spot_buy") BigDecimal spotBuy,
            @JsonProperty("spot_sell") BigDecimal spotSell) {
    }

    /** TaiwanStockTotalInstitutionalInvestors（整體市場三大法人買賣金額） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InstitutionalRow(
            @JsonProperty("date") String date,
            @JsonProperty("name") String name,
            @JsonProperty("buy") BigDecimal buy,
            @JsonProperty("sell") BigDecimal sell) {
    }
}
