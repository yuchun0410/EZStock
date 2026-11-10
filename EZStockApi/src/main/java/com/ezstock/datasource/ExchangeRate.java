package com.ezstock.datasource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 外部來源的匯率（臺灣銀行牌告），1 單位外幣可以換多少台幣。
 *
 * @param spotBuy  即期買入（銀行跟你買外幣的價格）
 * @param spotSell 即期賣出（銀行賣你外幣的價格）
 */
public record ExchangeRate(String currency, LocalDate date, BigDecimal spotBuy, BigDecimal spotSell) {

    /** 中價 = (買入 + 賣出) / 2，估算資產價值用，不偏向買或賣 */
    public BigDecimal mid() {
        return spotBuy.add(spotSell).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
