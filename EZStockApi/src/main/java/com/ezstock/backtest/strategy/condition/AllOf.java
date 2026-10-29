package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 組合條件：全部成立才成立（AND）。買進條件通常用這個，例如「站上 20 日均線」而且「突破 10 日高點」。
 *
 * AllOf 自己也是一個 Condition，所以可以跟一般條件放在一起、甚至再組合（組合模式 Composite Pattern）。
 * 回測引擎只要呼叫 isSatisfied，不用知道它是單一條件還是一組條件。
 */
public class AllOf implements Condition {

    private final List<Condition> conditions;

    public AllOf(List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("至少要有一個條件");
        }
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public boolean isSatisfied(ConditionContext context) {
        return conditions.stream().allMatch(condition -> condition.isSatisfied(context));
    }

    @Override
    public String describe() {
        return conditions.stream().map(Condition::describe).collect(Collectors.joining("，而且"));
    }

    public List<Condition> conditions() {
        return conditions;
    }
}
