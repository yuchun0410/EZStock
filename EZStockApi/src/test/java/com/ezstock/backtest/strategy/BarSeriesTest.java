package com.ezstock.backtest.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarSeriesTest {

    @Test
    @DisplayName("N 日最低價：只看前 N 天，不含今天")
    void lowestLowExcludesToday() {
        BarSeries series = TestBars.ohlc(
                "10,11,9,10",
                "10,12,8,11",   // 最低 8
                "11,13,10,12",
                "12,12,5,6");   // 今天最低 5，不能算進去

        assertThat(series.lowestLow(3, 3)).isEqualByComparingTo("8");
        assertThat(series.lowestLow(3, 2)).isEqualByComparingTo("8");
        assertThat(series.lowestLow(3, 1)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("N 日最高價：只看前 N 天，不含今天")
    void highestHighExcludesToday() {
        BarSeries series = TestBars.ohlc("10,11,9,10", "10,15,8,11", "11,13,10,12", "12,20,12,19");

        assertThat(series.highestHigh(3, 3)).isEqualByComparingTo("15");
        assertThat(series.highestHigh(3, 1)).isEqualByComparingTo("13");
    }

    @Test
    @DisplayName("N 日均線：包含今天在內最近 N 天收盤的平均")
    void movingAverage() {
        BarSeries series = TestBars.closes("10", "20", "30", "40");

        assertThat(series.movingAverage(3, 3)).isEqualByComparingTo("30");
        assertThat(series.movingAverage(3, 4)).isEqualByComparingTo("25");
        assertThat(series.movingAverage(1, 2)).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("歷史資料不足時丟出例外；hasHistory 可以先檢查")
    void notEnoughHistory() {
        BarSeries series = TestBars.closes("10", "20", "30");

        assertThat(series.hasHistory(2, 2)).isTrue();
        assertThat(series.hasHistory(2, 3)).isFalse();
        assertThatThrownBy(() -> series.lowestLow(2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> series.movingAverage(1, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
