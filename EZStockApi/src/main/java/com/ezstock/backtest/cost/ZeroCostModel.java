package com.ezstock.backtest.cost;

import com.ezstock.model.StockType;

import java.math.BigDecimal;

/** 不計任何交易成本。用來對照「成本吃掉了多少報酬」。 */
public class ZeroCostModel implements CostModel {

    @Override
    public BigDecimal buyCost(BigDecimal amount, StockType type) {
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal sellCost(BigDecimal amount, StockType type) {
        return BigDecimal.ZERO;
    }

    @Override
    public String describe() {
        return "不計交易成本";
    }
}
