package com.ezstock.dto;

import com.ezstock.model.StockType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一檔持股的現況。資料庫沒有這檔的股價時，price 之後的欄位都是 null。
 *
 * 美股的平均成本、現價、現值、損益都是美元；換算成台幣的另外放在 costTwd、marketValueTwd。
 *
 * @param currency      這檔股票的幣別（TWD / USD）
 * @param cost          成本 = 股數 × 平均成本
 * @param price         最新收盤價
 * @param priceDate     收盤價的日期
 * @param marketValue   現值 = 股數 × 最新收盤價
 * @param unrealizedPnl 未實現損益 = 現值 − 成本
 * @param returnPercent 報酬率（%）
 * @param costTwd        成本換算成台幣（台股跟 cost 一樣）；美股抓不到匯率時是 null
 * @param marketValueTwd 現值換算成台幣（台股跟 marketValue 一樣）；沒有股價或抓不到匯率時是 null
 */
public record HoldingResponse(String stockId, String name, StockType type, String currency,
                              long shares, BigDecimal averageCost, BigDecimal cost,
                              BigDecimal price, LocalDate priceDate,
                              BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal returnPercent,
                              BigDecimal costTwd, BigDecimal marketValueTwd) {
}
