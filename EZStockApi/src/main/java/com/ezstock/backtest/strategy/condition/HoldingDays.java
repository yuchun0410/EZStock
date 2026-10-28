package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

/** 持有滿 N 個交易日。只能當賣出條件（空手時永遠不成立） */
public class HoldingDays implements Condition {

    private final int days;

    public HoldingDays(int days) {
        this.days = Conditions.requirePositive(days, "持有天數");
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        return context.hasPosition() && context.holdingDays() >= days;
    }

    @Override
    public String describe() {
        return "持有滿 " + days + " 個交易日";
    }
}
