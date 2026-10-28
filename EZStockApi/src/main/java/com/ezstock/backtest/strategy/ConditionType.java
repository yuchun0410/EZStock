package com.ezstock.backtest.strategy;

import com.ezstock.backtest.strategy.condition.CloseAboveHighestHigh;
import com.ezstock.backtest.strategy.condition.CloseAboveMovingAverage;
import com.ezstock.backtest.strategy.condition.CloseBelowLowestLow;
import com.ezstock.backtest.strategy.condition.CloseBelowMovingAverage;
import com.ezstock.backtest.strategy.condition.Condition;
import com.ezstock.backtest.strategy.condition.HoldingDays;
import com.ezstock.backtest.strategy.condition.StopLoss;
import com.ezstock.backtest.strategy.condition.TakeProfit;

import java.math.BigDecimal;

/**
 * 使用者可以選的條件種類。前端的下拉選單、欄位單位、預設值、能不能當買進條件，全部從這裡來，
 * 新增一種條件只要：寫一個 Condition 類別 + 在這裡加一行，前端選單就會自動多一個選項。
 */
public enum ConditionType {

    CLOSE_BELOW_LOWEST_LOW("收盤跌破前 N 日最低價", ValueKind.DAYS, true, 5),
    CLOSE_ABOVE_HIGHEST_HIGH("收盤突破前 N 日最高價", ValueKind.DAYS, true, 20),
    CLOSE_ABOVE_MOVING_AVERAGE("收盤站上 N 日均線", ValueKind.DAYS, true, 20),
    CLOSE_BELOW_MOVING_AVERAGE("收盤跌破 N 日均線", ValueKind.DAYS, true, 20),
    TAKE_PROFIT("漲 X% 停利", ValueKind.PERCENT, false, 10),
    STOP_LOSS("跌 X% 停損", ValueKind.PERCENT, false, 5),
    HOLDING_DAYS("持有滿 N 個交易日", ValueKind.DAYS, false, 20);

    private final String label;
    private final ValueKind valueKind;
    private final boolean entryAllowed;
    private final int defaultValue;

    ConditionType(String label, ValueKind valueKind, boolean entryAllowed, int defaultValue) {
        this.label = label;
        this.valueKind = valueKind;
        this.entryAllowed = entryAllowed;
        this.defaultValue = defaultValue;
    }

    /**
     * 依照數值建立對應的 Condition。數值超出範圍丟 IllegalArgumentException。
     * switch 要列出每一種，少寫一種編譯器就會報錯，不會漏掉。
     */
    public Condition create(BigDecimal value) {
        valueKind.validate(this, value);
        return switch (this) {
            case CLOSE_BELOW_LOWEST_LOW -> new CloseBelowLowestLow(value.intValueExact());
            case CLOSE_ABOVE_HIGHEST_HIGH -> new CloseAboveHighestHigh(value.intValueExact());
            case CLOSE_ABOVE_MOVING_AVERAGE -> new CloseAboveMovingAverage(value.intValueExact());
            case CLOSE_BELOW_MOVING_AVERAGE -> new CloseBelowMovingAverage(value.intValueExact());
            case TAKE_PROFIT -> new TakeProfit(value);
            case STOP_LOSS -> new StopLoss(value);
            case HOLDING_DAYS -> new HoldingDays(value.intValueExact());
        };
    }

    public String label() {
        return label;
    }

    public ValueKind valueKind() {
        return valueKind;
    }

    /** 停利、停損、持有天數需要「已經持有」才有意義，不能當買進條件 */
    public boolean entryAllowed() {
        return entryAllowed;
    }

    public int defaultValue() {
        return defaultValue;
    }

    /** 條件的數值是「天數」還是「百分比」，以及允許的範圍 */
    public enum ValueKind {
        DAYS("日", BigDecimal.ONE, BigDecimal.valueOf(240)),
        PERCENT("%", new BigDecimal("0.1"), BigDecimal.valueOf(100));

        private final String unit;
        private final BigDecimal min;
        private final BigDecimal max;

        ValueKind(String unit, BigDecimal min, BigDecimal max) {
            this.unit = unit;
            this.min = min;
            this.max = max;
        }

        void validate(ConditionType type, BigDecimal value) {
            if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                throw new IllegalArgumentException(String.format("「%s」的數值必須在 %s～%s %s 之間：%s",
                        type.label, min.toPlainString(), max.toPlainString(), unit,
                        value == null ? "未填" : value.toPlainString()));
            }
            if (this == DAYS && value.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("「" + type.label + "」的天數必須是整數：" + value.toPlainString());
            }
        }

        public String unit() {
            return unit;
        }

        public BigDecimal min() {
            return min;
        }

        public BigDecimal max() {
            return max;
        }
    }
}
