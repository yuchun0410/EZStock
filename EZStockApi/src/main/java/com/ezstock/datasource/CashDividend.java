package com.ezstock.datasource;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 外部來源的一筆現金股利，與資料來源格式無關 */
public record CashDividend(String stockId, LocalDate exDate, BigDecimal cashDividend) {
}
