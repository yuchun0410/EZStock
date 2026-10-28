package com.ezstock.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StockSplitTest {

    private final Stock stock = new Stock("0050", "元大台灣50", StockType.ETF, "ETF");

    @Test
    @DisplayName("1 拆 4：188.65 → 47.16，比例修正成剛好 4")
    void split() {
        assertThat(split("188.65", "47.16").shareRatio()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("反分割 2 合 1：50 → 100，比例是 0.5")
    void reverseSplit() {
        assertThat(split("50", "100").shareRatio()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("不是整數比例時保留原本的數字")
    void irregularRatio() {
        // 30 / 20 = 1.5，跟 2 差很多，不修正
        assertThat(split("30", "20").shareRatio()).isEqualByComparingTo("1.5");
    }

    private StockSplit split(String before, String after) {
        return new StockSplit(stock, LocalDate.of(2025, 6, 18), SplitType.SPLIT,
                new BigDecimal(before), new BigDecimal(after));
    }
}
