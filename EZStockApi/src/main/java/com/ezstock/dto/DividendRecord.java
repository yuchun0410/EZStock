package com.ezstock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一筆除息紀錄。
 *
 * @param exDate            除息日
 * @param cashDividend      每股現金股利（元）
 * @param closeBeforeExDate 除息前一個交易日的收盤價；資料庫沒有那天的價格時是 null
 * @param yieldPercent      這次配息的殖利率（%）= 現金股利 / 除息前收盤 × 100；算不出來時是 null
 */
public record DividendRecord(LocalDate exDate, BigDecimal cashDividend,
                             BigDecimal closeBeforeExDate, BigDecimal yieldPercent) {
}
