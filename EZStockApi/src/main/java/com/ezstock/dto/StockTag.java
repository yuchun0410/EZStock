package com.ezstock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 文章標記的股票，附上最新一天的收盤與漲跌（論壇只讀資料庫，不為了顯示漲跌去打外部 API）。
 * 資料庫沒有最近的股價時，close 之後的欄位都是 null。
 *
 * @param change        漲跌（跟前一個交易日比）
 * @param changePercent 漲跌幅（%）
 * @param priceDate     收盤價的日期
 */
public record StockTag(String stockId, String name, BigDecimal close, BigDecimal change,
                       BigDecimal changePercent, LocalDate priceDate) {
}
