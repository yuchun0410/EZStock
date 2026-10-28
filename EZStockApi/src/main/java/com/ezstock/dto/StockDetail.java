package com.ezstock.dto;

import com.ezstock.model.StockType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 個股頁的基本資料與最新行情。
 * 還沒有匯入價格的股票，latestDate 之後的欄位都是 null。
 *
 * @param change        漲跌（元），跟前一個交易日收盤比
 * @param changePercent 漲跌幅（%），例如 1.25 代表上漲 1.25%
 */
public record StockDetail(String stockId, String name, StockType type, String industry,
                          LocalDate latestDate, BigDecimal latestClose,
                          BigDecimal change, BigDecimal changePercent) {
}
