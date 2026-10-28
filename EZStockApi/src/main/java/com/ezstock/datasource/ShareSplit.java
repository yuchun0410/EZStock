package com.ezstock.datasource;

import com.ezstock.model.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 外部來源的一筆股票分割或面額變更，與資料來源格式無關 */
public record ShareSplit(String stockId, LocalDate date, SplitType type,
                         BigDecimal beforePrice, BigDecimal afterPrice) {
}
