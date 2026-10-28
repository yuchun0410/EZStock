package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.BarSeries;
import com.ezstock.backtest.strategy.ConditionContext;

/** 收盤突破前 N 日最高價（突破策略常用） */
public class CloseAboveHighestHigh implements Condition {

    private final int days;

    public CloseAboveHighestHigh(int days) {
        this.days = Conditions.requirePositive(days, "天數");
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        BarSeries series = context.series();
        int today = context.index();
        if (!series.hasHistory(today, days)) {
            return false;
        }
        return series.close(today).compareTo(series.highestHigh(today, days)) > 0;
    }

    @Override
    public String describe() {
        return "收盤突破前 " + days + " 日最高價";
    }
}
