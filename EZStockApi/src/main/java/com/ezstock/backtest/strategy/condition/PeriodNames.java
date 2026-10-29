package com.ezstock.backtest.strategy.condition;

import java.util.Map;

/**
 * 常用天數的俗稱，讓說明文字跟看盤軟體的講法一樣，例如 20 日線 = 月線。
 * 台股一個月大約 20 個交易日、一季約 60 個、一年約 240 個。
 */
public final class PeriodNames {

    private static final Map<Integer, String> MOVING_AVERAGE = Map.of(
            5, "週線", 10, "雙週線", 20, "月線", 60, "季線", 120, "半年線", 240, "年線");

    private static final Map<Integer, String> PERIOD = Map.of(
            5, "一週", 10, "兩週", 20, "一個月", 60, "一季", 120, "半年", 240, "一年");

    private PeriodNames() {
    }

    /** 20 → 「20 日線（月線）」；不是常用天數就是「17 日均線」 */
    public static String movingAverage(int days) {
        String alias = MOVING_AVERAGE.get(days);
        return alias == null ? days + " 日均線" : days + " 日線（" + alias + "）";
    }

    /** 20 → 「前 20 日（一個月）」；不是常用天數就是「前 17 日」 */
    public static String period(int days) {
        String alias = PERIOD.get(days);
        return alias == null ? "前 " + days + " 日" : "前 " + days + " 日（" + alias + "）";
    }
}
