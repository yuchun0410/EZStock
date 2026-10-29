package com.ezstock.backtest.strategy;

/**
 * 判斷條件時的「當下狀況」：在哪一檔的哪一天，以及手上有沒有部位。
 *
 * 用一個物件包起來，而不是在 Condition 的方法上放一堆參數：
 * 之後條件需要更多資訊（例如大盤、成交量），只要在這裡加欄位，所有 Condition 的方法簽章都不用改。
 *
 * @param series   K 棒序列
 * @param index    今天是第幾根 K 棒（收盤後判斷）
 * @param position 目前的部位；空手時是 null
 */
public record ConditionContext(BarSeries series, int index, OpenPosition position) {

    public boolean hasPosition() {
        return position != null;
    }

    /** 從買進那天到今天，經過了幾個交易日 */
    public int holdingDays() {
        if (position == null) {
            throw new IllegalStateException("空手時沒有持有天數");
        }
        return index - position.entryIndex();
    }
}
