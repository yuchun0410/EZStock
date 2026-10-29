package com.ezstock.backtest.strategy;

import com.ezstock.backtest.EquityPoint;
import com.ezstock.backtest.PerformanceMetrics;

import java.math.BigDecimal;
import java.util.List;

/**
 * 條件回測的結果。
 *
 * @param winRatePercent      勝率（%）：已結束的交易中，賺錢的比例；沒有已結束的交易時是 null
 * @param averageReturnPercent 已結束交易的平均報酬率（%）；沒有時是 null
 * @param exposurePercent     有持股的天數占全部天數的比例（%），代表資金「在市場裡」的時間
 * @param pendingSignal       最後一天收盤出現的訊號（隔天開盤才會執行），沒有則是 null
 */
public record StrategyResult(String stockId, String name,
                             BigDecimal finalValue, BigDecimal totalDividends, BigDecimal totalCosts,
                             PerformanceMetrics metrics,
                             int tradeCount, BigDecimal winRatePercent, BigDecimal averageReturnPercent,
                             BigDecimal exposurePercent,
                             List<Trade> trades, List<EquityPoint> equityCurve,
                             String pendingSignal) {
}
