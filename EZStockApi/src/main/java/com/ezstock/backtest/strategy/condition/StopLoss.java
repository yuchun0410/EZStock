package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

import java.math.BigDecimal;

/** 停損：收盤價比買進價跌了至少 X%。只能當賣出條件（空手時永遠不成立） */
public class StopLoss implements Condition {

    private final BigDecimal percent;

    public StopLoss(BigDecimal percent) {
        this.percent = Conditions.requirePositive(percent, "停損幅度");
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        if (!context.hasPosition()) {
            return false;
        }
        BigDecimal change = Conditions.changePercent(context);
        // 跌 8% 時 change 是 -8，要 ≤ -8 才成立
        return change.compareTo(percent.negate()) <= 0;
    }

    @Override
    public String describe() {
        return "跌 " + percent.stripTrailingZeros().toPlainString() + "% 停損";
    }
}
