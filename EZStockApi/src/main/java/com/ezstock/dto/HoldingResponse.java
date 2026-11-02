package com.ezstock.dto;

import com.ezstock.model.StockType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一檔持股的現況。資料庫沒有這檔的股價時，price 之後的欄位都是 null。
 *
 * @param cost          成本 = 股數 × 平均成本
 * @param price         最新收盤價
 * @param priceDate     收盤價的日期
 * @param marketValue   現值 = 股數 × 最新收盤價
 * @param unrealizedPnl 未實現損益 = 現值 − 成本
 * @param returnPercent 報酬率（%）
 */
public record HoldingResponse(String stockId, String name, StockType type,
                              long shares, BigDecimal averageCost, BigDecimal cost,
                              BigDecimal price, LocalDate priceDate,
                              BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal returnPercent) {
}
