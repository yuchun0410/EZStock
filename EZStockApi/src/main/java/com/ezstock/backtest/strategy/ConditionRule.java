package com.ezstock.backtest.strategy;

import java.math.BigDecimal;

/** 使用者設定的一條規則：哪一種條件、數值多少。例如（CLOSE_BELOW_LOWEST_LOW, 5）= 破 5 日低點 */
public record ConditionRule(ConditionType type, BigDecimal value) {
}
