package com.ezstock.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 換算資產用的匯率：1 單位外幣 = rate 元台幣。
 *
 * @param date 牌告日期（假日沒有報價，會是前一個營業日）
 */
public record FxRate(String currency, BigDecimal rate, LocalDate date) {
}
