package com.ezstock.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 資產曲線上的一點：某一天收盤後，持股市值 + 現金 */
public record EquityPoint(LocalDate date, BigDecimal value) {
}
