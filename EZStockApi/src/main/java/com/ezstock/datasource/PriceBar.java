package com.ezstock.datasource;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 外部來源的一根日 K，與資料來源格式無關 */
public record PriceBar(String stockId, LocalDate date,
                       BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                       long volume) {
}
