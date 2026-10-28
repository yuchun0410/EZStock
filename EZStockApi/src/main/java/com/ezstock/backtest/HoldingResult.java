package com.ezstock.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一檔標的「買進後一直持有」的模擬結果。
 *
 * @param buyDate        買進日（起始日之後的第一個交易日）
 * @param buyPrice       買進價（當天開盤價）
 * @param initialShares  第一次買進的股數
 * @param finalShares    期末股數（股利再投入會變多）
 * @param cash           期末剩下的現金（買不滿一股的零頭、沒有再投入的股利）
 * @param finalValue     期末資產 = 期末股數 × 最後收盤 + 現金（市值，還沒扣賣出成本）
 * @param totalDividends 期間領到的現金股利合計
 * @param totalCosts     期間付出的交易成本合計（手續費）
 * @param warnings       回測過程發現的疑慮，例如價格異常跳動，要顯示給使用者
 */
public record HoldingResult(String stockId, String name,
                            LocalDate buyDate, BigDecimal buyPrice,
                            long initialShares, long finalShares, BigDecimal cash,
                            BigDecimal finalValue, BigDecimal totalDividends, BigDecimal totalCosts,
                            PerformanceMetrics metrics, List<EquityPoint> equityCurve,
                            List<String> warnings) {
}
