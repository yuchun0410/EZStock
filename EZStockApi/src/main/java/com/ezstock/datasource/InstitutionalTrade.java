package com.ezstock.datasource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 整體市場某一類法人某一天的買進、賣出金額（元）。
 *
 * @param investor 外部來源的法人名稱，例如 Foreign_Investor（外資）、Investment_Trust（投信）、
 *                 Dealer_self（自營商自行買賣）、Dealer_Hedging（自營商避險）、total（合計）
 */
public record InstitutionalTrade(LocalDate date, String investor, BigDecimal buy, BigDecimal sell) {
}
