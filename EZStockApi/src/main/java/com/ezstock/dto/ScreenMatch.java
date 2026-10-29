package com.ezstock.dto;

import com.ezstock.model.StockType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 符合條件的一檔股票，以及它最新一天的行情 */
public record ScreenMatch(String stockId, String name, StockType type, String industry,
                          LocalDate date, BigDecimal close, BigDecimal change, BigDecimal changePercent) {
}
