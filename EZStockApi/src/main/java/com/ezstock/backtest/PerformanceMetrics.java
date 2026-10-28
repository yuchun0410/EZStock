package com.ezstock.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 回測績效指標。標的比較和之後的條件回測都會用到，所以獨立出來。
 *
 * @param totalReturnPercent      總報酬率（%）=（期末 - 本金）/ 本金 × 100
 * @param annualizedReturnPercent 年化報酬率（%），把總報酬換算成「平均每年」；期間不到 1 年時是 null
 * @param maxDrawdownPercent      最大回落（%）：期間內從最高點往下跌最多的幅度，是負數或 0
 */
public record PerformanceMetrics(BigDecimal totalReturnPercent,
                                 BigDecimal annualizedReturnPercent,
                                 BigDecimal maxDrawdownPercent) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final double DAYS_PER_YEAR = 365.0;

    /**
     * 未滿一年不算年化。
     * 3 天賺 10% 換算成一年會變成幾億 %，數字沒有意義還會誤導人，
     * 投資績效的業界標準（GIPS）也規定未滿一年的報酬不能年化。
     */
    static final long MIN_DAYS_FOR_ANNUALIZED = 365;

    public static PerformanceMetrics of(BigDecimal initialCapital, List<EquityPoint> curve) {
        if (curve.isEmpty()) {
            throw new IllegalArgumentException("資產曲線不能是空的");
        }
        BigDecimal finalValue = curve.getLast().value();
        BigDecimal totalReturn = finalValue.subtract(initialCapital)
                .multiply(HUNDRED)
                .divide(initialCapital, 2, RoundingMode.HALF_UP);

        long days = ChronoUnit.DAYS.between(curve.getFirst().date(), curve.getLast().date());
        BigDecimal annualized = null;
        if (days >= MIN_DAYS_FOR_ANNUALIZED) {
            // 年化報酬 =（期末 / 本金）^(1 / 年數) - 1。次方運算 BigDecimal 做不到，這裡改用 double，算完再轉回來
            double growth = finalValue.doubleValue() / initialCapital.doubleValue();
            double years = days / DAYS_PER_YEAR;
            double rate = Math.pow(growth, 1 / years) - 1;
            annualized = BigDecimal.valueOf(rate * 100).setScale(2, RoundingMode.HALF_UP);
        }

        return new PerformanceMetrics(totalReturn, annualized, maxDrawdown(curve));
    }

    /** 一路記住目前為止的最高點，每天算「現在比最高點跌了幾 %」，取最小（跌最深）的那次 */
    private static BigDecimal maxDrawdown(List<EquityPoint> curve) {
        BigDecimal peak = curve.getFirst().value();
        BigDecimal worst = BigDecimal.ZERO;
        for (EquityPoint point : curve) {
            if (point.value().compareTo(peak) > 0) {
                peak = point.value();
            }
            if (peak.signum() > 0) {
                BigDecimal drawdown = point.value().subtract(peak)
                        .multiply(HUNDRED)
                        .divide(peak, 2, RoundingMode.HALF_UP);
                if (drawdown.compareTo(worst) < 0) {
                    worst = drawdown;
                }
            }
        }
        return worst;
    }
}
