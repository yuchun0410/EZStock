package com.ezstock.backtest.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一筆完整的交易：買進到賣出。回測結束時還沒賣的，exitDate 是 null（持有中，用最後收盤價估值）。
 *
 * @param shares        賣出時的股數（遇到股票分割會變多）
 * @param profit        淨損益 = 賣出淨額 - 買進總成本 + 持有期間領到的股利（已扣手續費和證交稅）
 * @param returnPercent 報酬率（%）= 淨損益 / 買進總成本 × 100
 * @param holdingDays   持有幾個交易日
 * @param exitReason    為什麼賣出，例如「漲 10% 停利」；持有中是「回測結束時仍持有」
 */
public record Trade(LocalDate entryDate, BigDecimal entryPrice,
                    LocalDate exitDate, BigDecimal exitPrice,
                    long shares, BigDecimal profit, BigDecimal returnPercent,
                    int holdingDays, String exitReason) {

    public boolean isOpen() {
        return exitDate == null;
    }

    public boolean isWin() {
        return profit.signum() > 0;
    }
}
