package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.ConditionContext;

/**
 * 回測的條件：「今天收盤後，這個條件成立嗎？」
 *
 * 每一種條件都是一個類別，實作這個介面。
 * 回測引擎只認得 Condition，不知道背後是均線、停損還是持有天數，
 * 所以新增一種條件只要多寫一個類別，引擎完全不用改（開放封閉原則）。
 */
public interface Condition {

    /** 今天（context.index()）收盤後，條件是否成立。資料不足時回傳 false，不要丟例外 */
    boolean isSatisfied(ConditionContext context);

    /** 給人看的說明，例如「收盤跌破前 5 日最低價」，會顯示在畫面和 log */
    String describe();
}
