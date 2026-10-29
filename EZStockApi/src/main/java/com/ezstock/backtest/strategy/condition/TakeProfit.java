package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

import java.math.BigDecimal;

/** 停利：收盤價比買進價漲了至少 X%。只能當賣出條件（空手時永遠不成立） */
public class TakeProfit implements Condition {

    private final BigDecimal percent;

    public TakeProfit(BigDecimal percent) {
        this.percent = Conditions.requirePositive(percent, "停利幅度");
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        if (!context.hasPosition()) {
            return false;
        }
        BigDecimal change = Conditions.changePercent(context);
        return change.compareTo(percent) >= 0;
    }

    @Override
    public String describe() {
        return "漲 " + percent.stripTrailingZeros().toPlainString() + "% 停利";
    }
}
