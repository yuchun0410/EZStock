package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 組合條件：任一個成立就成立（OR）。賣出條件通常用這個，例如「漲 10% 停利」或「跌 5% 停損」或「持有 20 天」。
 */
public class AnyOf implements Condition {

    private final List<Condition> conditions;

    public AnyOf(List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("至少要有一個條件");
        }
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        return firstSatisfied(context).isPresent();
    }

    /** 第一個成立的條件，回測時拿來記錄「這筆交易是因為什麼原因賣出」 */
    public Optional<Condition> firstSatisfied(ConditionContext context) {
        return conditions.stream().filter(condition -> condition.isSatisfied(context)).findFirst();
    }

    @Override
    public String describe() {
        return conditions.stream().map(Condition::describe).collect(Collectors.joining("，或"));
    }

    public List<Condition> conditions() {
        return conditions;
    }
}
