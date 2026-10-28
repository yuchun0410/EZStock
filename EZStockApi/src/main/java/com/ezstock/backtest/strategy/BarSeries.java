package com.ezstock.backtest.strategy;

import com.ezstock.model.DailyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 一檔股票依日期排好的 K 棒，加上條件會用到的技術指標。
 * 條件只透過這個類別取得價格，不直接碰 DailyPrice，之後要換資料來源或加快取都只改這裡。
 *
 * 所有方法的 index 都是「第幾根 K 棒」，0 是最早的那一天。
 */
public class BarSeries {

    private final List<DailyPrice> bars;

    public BarSeries(List<DailyPrice> bars) {
        this.bars = List.copyOf(bars);
    }

    public int size() {
        return bars.size();
    }

    public DailyPrice bar(int index) {
        return bars.get(index);
    }

    public LocalDate date(int index) {
        return bars.get(index).getTradeDate();
    }

    public BigDecimal open(int index) {
        return bars.get(index).getOpenPrice();
    }

    public BigDecimal close(int index) {
        return bars.get(index).getClosePrice();
    }

    /**
     * 前 days 天（不含今天）的最低價。
     * 「收盤跌破 5 日低點」要跟「前 5 天」比，如果把今天算進去，今天的最低價一定 ≤ 收盤，永遠不會成立。
     */
    public BigDecimal lowestLow(int index, int days) {
        requireHistory(index, days, days);
        BigDecimal lowest = bars.get(index - 1).getLowPrice();
        for (int i = index - days; i < index - 1; i++) {
            lowest = lowest.min(bars.get(i).getLowPrice());
        }
        return lowest;
    }

    /** 前 days 天（不含今天）的最高價 */
    public BigDecimal highestHigh(int index, int days) {
        requireHistory(index, days, days);
        BigDecimal highest = bars.get(index - 1).getHighPrice();
        for (int i = index - days; i < index - 1; i++) {
            highest = highest.max(bars.get(i).getHighPrice());
        }
        return highest;
    }

    /** 包含今天在內，最近 days 天收盤價的平均（一般說的 N 日均線） */
    public BigDecimal movingAverage(int index, int days) {
        requireHistory(index, days, days - 1);
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - days + 1; i <= index; i++) {
            sum = sum.add(bars.get(i).getClosePrice());
        }
        return sum.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    /** 今天之前是否至少有 bars 根資料，可以算指標 */
    public boolean hasHistory(int index, int barsBefore) {
        return index >= barsBefore && index < bars.size();
    }

    private void requireHistory(int index, int days, int barsBefore) {
        if (days <= 0) {
            throw new IllegalArgumentException("天數必須大於 0：" + days);
        }
        if (!hasHistory(index, barsBefore)) {
            throw new IllegalArgumentException(
                    "第 " + index + " 根 K 棒之前的資料不足 " + barsBefore + " 根，無法計算 " + days + " 日指標");
        }
    }
}
