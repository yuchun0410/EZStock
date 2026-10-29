package com.ezstock.backtest.strategy;

import com.ezstock.backtest.strategy.condition.CloseAboveHighestHigh;
import com.ezstock.backtest.strategy.condition.CloseAboveMovingAverage;
import com.ezstock.backtest.strategy.condition.CloseBelowLowestLow;
import com.ezstock.backtest.strategy.condition.CloseBelowMovingAverage;
import com.ezstock.backtest.strategy.condition.Condition;
import com.ezstock.backtest.strategy.condition.HoldingDays;
import com.ezstock.backtest.strategy.condition.StopLoss;
import com.ezstock.backtest.strategy.condition.TakeProfit;

import com.ezstock.backtest.strategy.condition.PeriodNames;

import java.math.BigDecimal;
import java.util.List;

/**
 * 使用者可以選的條件種類。前端的下拉選單、欄位單位、預設值、能不能當買進條件，全部從這裡來，
 * 新增一種條件只要：寫一個 Condition 類別 + 在這裡加一行，前端選單就會自動多一個選項。
 */
public enum ConditionType {

    // 順序就是前端下拉選單的順序：最常用的均線放最前面
    CLOSE_ABOVE_MOVING_AVERAGE("收盤站上均線", ValueKind.DAYS, true, 20, Presets.MOVING_AVERAGE),
    CLOSE_BELOW_MOVING_AVERAGE("收盤跌破均線", ValueKind.DAYS, true, 20, Presets.MOVING_AVERAGE),
    CLOSE_BELOW_LOWEST_LOW("收盤跌破近期最低價", ValueKind.DAYS, true, 5, Presets.PERIOD),
    CLOSE_ABOVE_HIGHEST_HIGH("收盤突破近期最高價", ValueKind.DAYS, true, 20, Presets.PERIOD),
    TAKE_PROFIT("停利（上漲）", ValueKind.PERCENT, false, 10, List.of()),
    STOP_LOSS("停損（下跌）", ValueKind.PERCENT, false, 5, List.of()),
    HOLDING_DAYS("持有天數", ValueKind.DAYS, false, 20, List.of());

    /** 常用天數，前端顯示成下拉選單；空的代表讓使用者自己輸入數字 */
    public record Option(int value, String label) {
    }

    private static final class Presets {
        private static final int[] COMMON_DAYS = {5, 10, 20, 60, 120, 240};
        static final List<Option> MOVING_AVERAGE = build(true);
        static final List<Option> PERIOD = build(false);

        private static List<Option> build(boolean movingAverage) {
            return java.util.Arrays.stream(COMMON_DAYS)
                    .mapToObj(days -> new Option(days,
                            movingAverage ? PeriodNames.movingAverage(days) : PeriodNames.period(days)))
                    .toList();
        }
    }

    private final String label;
    private final ValueKind valueKind;
    private final boolean entryAllowed;
    private final int defaultValue;
    private final List<Option> options;

    ConditionType(String label, ValueKind valueKind, boolean entryAllowed, int defaultValue, List<Option> options) {
        this.label = label;
        this.valueKind = valueKind;
        this.entryAllowed = entryAllowed;
        this.defaultValue = defaultValue;
        this.options = options;
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

    /**
     * 判斷這個條件，今天之前需要幾天的歷史資料（例如季線要 60 天）。
     * 停利、停損、持有天數不需要歷史價格，回傳 0。
     */
    public int lookbackDays(BigDecimal value) {
        return switch (this) {
            case CLOSE_BELOW_LOWEST_LOW, CLOSE_ABOVE_HIGHEST_HIGH,
                 CLOSE_ABOVE_MOVING_AVERAGE, CLOSE_BELOW_MOVING_AVERAGE -> value.intValue();
            case TAKE_PROFIT, STOP_LOSS, HOLDING_DAYS -> 0;
        };
    }

    /** 常用天數選項（均線：週線、月線、季線…；高低點：一週、一個月…）；百分比和持有天數是空的 */
    public List<Option> options() {
        return options;
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
