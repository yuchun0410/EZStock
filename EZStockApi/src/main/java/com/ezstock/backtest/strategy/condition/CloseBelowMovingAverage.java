package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.BarSeries;
import com.ezstock.backtest.strategy.ConditionContext;

/** 收盤價低於 N 日均線 */
public class CloseBelowMovingAverage implements Condition {

    private final int days;

    public CloseBelowMovingAverage(int days) {
        this.days = Conditions.requirePositive(days, "天數");
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        BarSeries series = context.series();
        int today = context.index();
        if (!series.hasHistory(today, days - 1)) {
            return false;
        }
        return series.close(today).compareTo(series.movingAverage(today, days)) < 0;
    }

    @Override
    public String describe() {
        return "收盤跌破 " + PeriodNames.movingAverage(days);
    }
}
