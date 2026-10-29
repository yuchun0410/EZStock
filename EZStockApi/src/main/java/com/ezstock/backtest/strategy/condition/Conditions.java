package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 各個條件共用的小工具。package-private：只給同一個套件的條件類別用 */
final class Conditions {

    private Conditions() {
    }

    static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必須大於 0：" + value);
        }
        return value;
    }

    static BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + "必須大於 0：" + value);
        }
        return value;
    }

    /** 今天收盤相對買進價漲跌了幾 %（漲是正數、跌是負數） */
    static BigDecimal changePercent(ConditionContext context) {
        BigDecimal entry = context.position().entryPrice();
        BigDecimal close = context.series().close(context.index());
        return close.subtract(entry).multiply(BigDecimal.valueOf(100)).divide(entry, 4, RoundingMode.HALF_UP);
    }
}
