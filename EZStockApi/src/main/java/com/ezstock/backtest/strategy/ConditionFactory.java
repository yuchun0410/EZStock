package com.ezstock.backtest.strategy;

import com.ezstock.backtest.strategy.condition.AllOf;
import com.ezstock.backtest.strategy.condition.AnyOf;
import com.ezstock.backtest.strategy.condition.Condition;

import java.util.List;

/**
 * 把使用者在表單上設定的規則，變成回測引擎看得懂的 Condition 物件（工廠模式）。
 * 呼叫的人不用知道有哪些 Condition 類別、怎麼 new，只要給規則就好。
 *
 * 規則數量、參數範圍不合理時丟 IllegalArgumentException，由 Service 轉成 400。
 */
public final class ConditionFactory {

    public static final int MAX_RULES = 3;

    private ConditionFactory() {
    }

    /** 買進條件：全部成立才買（AND）。停利、停損、持有天數不能當買進條件 */
    public static Condition entry(List<ConditionRule> rules) {
        requireCount(rules, "買進");
        for (ConditionRule rule : rules) {
            if (!rule.type().entryAllowed()) {
                throw new IllegalArgumentException("「" + rule.type().label() + "」只能當賣出條件");
            }
        }
        List<Condition> conditions = create(rules);
        return conditions.size() == 1 ? conditions.getFirst() : new AllOf(conditions);
    }

    /** 賣出條件：任一個成立就賣（OR）。一律包成 AnyOf，回測時才能記錄是哪一個條件觸發 */
    public static AnyOf exit(List<ConditionRule> rules) {
        requireCount(rules, "賣出");
        return new AnyOf(create(rules));
    }

    private static List<Condition> create(List<ConditionRule> rules) {
        return rules.stream().map(rule -> rule.type().create(rule.value())).toList();
    }

    private static void requireCount(List<ConditionRule> rules, String side) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("請至少設定 1 個" + side + "條件");
        }
        if (rules.size() > MAX_RULES) {
            throw new IllegalArgumentException(side + "條件最多 " + MAX_RULES + " 個");
        }
    }
}
