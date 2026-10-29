package com.ezstock.backtest.strategy.condition;

import com.ezstock.backtest.strategy.BarSeries;
import com.ezstock.backtest.strategy.ConditionContext;
import com.ezstock.backtest.strategy.OpenPosition;
import com.ezstock.backtest.strategy.TestBars;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 每一種條件各一組測試（@Nested 讓報告依條件分組）。
 * 每組都測「成立」「不成立」「資料不足或空手時不成立」。
 */
class ConditionTest {

    /** 空手（沒有部位）時的情境 */
    private static ConditionContext flat(BarSeries series, int index) {
        return new ConditionContext(series, index, null);
    }

    /** 持有中：在 entryIndex 那天用 entryPrice 買進 */
    private static ConditionContext holding(BarSeries series, int index, int entryIndex, String entryPrice) {
        return new ConditionContext(series, index, new OpenPosition(entryIndex, new BigDecimal(entryPrice)));
    }

    @Nested
    @DisplayName("收盤跌破前 N 日最低價")
    class CloseBelowLowestLowTest {

        private final BarSeries series = TestBars.ohlc(
                "100,101,98,100",
                "100,102,97,101",   // 前 3 日最低是 97
                "101,103,99,102",
                "102,102,96,96.5",  // 收盤 96.5 < 97 → 破
                "97,98,97,97");     // 前 3 日最低變成 96，收盤 97 沒破

        private final Condition condition = new CloseBelowLowestLow(3);

        @Test
        @DisplayName("收盤低於前 N 日最低價才成立")
        void satisfied() {
            assertThat(condition.isSatisfied(flat(series, 3))).isTrue();
            assertThat(condition.isSatisfied(flat(series, 4))).isFalse();
        }

        @Test
        @DisplayName("前面不足 N 天時不成立，不會丟例外")
        void notEnoughHistory() {
            assertThat(condition.isSatisfied(flat(series, 2))).isFalse();
        }
    }

    @Nested
    @DisplayName("收盤突破前 N 日最高價")
    class CloseAboveHighestHighTest {

        @Test
        @DisplayName("收盤高於前 N 日最高價才成立")
        void satisfied() {
            BarSeries series = TestBars.ohlc("100,105,99,104", "104,106,103,105", "105,108,105,107", "107,107,104,105");
            Condition condition = new CloseAboveHighestHigh(2);

            assertThat(condition.isSatisfied(flat(series, 2))).isTrue();   // 107 > 106
            assertThat(condition.isSatisfied(flat(series, 3))).isFalse();  // 105 < 108
        }
    }

    @Nested
    @DisplayName("均線")
    class MovingAverageTest {

        // 3 日均線：第 2 天 (10+20+30)/3 = 20，第 3 天 (20+30+15)/3 ≈ 21.67
        private final BarSeries series = TestBars.closes("10", "20", "30", "15");

        @Test
        @DisplayName("站上均線：收盤 30 > 均線 20")
        void above() {
            Condition condition = new CloseAboveMovingAverage(3);
            assertThat(condition.isSatisfied(flat(series, 2))).isTrue();
            assertThat(condition.isSatisfied(flat(series, 3))).isFalse();
        }

        @Test
        @DisplayName("跌破均線：收盤 15 < 均線 21.67")
        void below() {
            Condition condition = new CloseBelowMovingAverage(3);
            assertThat(condition.isSatisfied(flat(series, 3))).isTrue();
            assertThat(condition.isSatisfied(flat(series, 2))).isFalse();
        }

        @Test
        @DisplayName("資料不足 N 天時不成立")
        void notEnoughHistory() {
            assertThat(new CloseAboveMovingAverage(3).isSatisfied(flat(series, 1))).isFalse();
            assertThat(new CloseBelowMovingAverage(3).isSatisfied(flat(series, 1))).isFalse();
        }
    }

    @Nested
    @DisplayName("停利、停損、持有天數")
    class ExitConditionTest {

        private final BarSeries series = TestBars.closes("100", "105", "110", "92", "95");

        @Test
        @DisplayName("停利：漲幅剛好等於設定值也算成立")
        void takeProfit() {
            Condition condition = new TakeProfit(new BigDecimal("10"));
            assertThat(condition.isSatisfied(holding(series, 1, 0, "100"))).isFalse(); // +5%
            assertThat(condition.isSatisfied(holding(series, 2, 0, "100"))).isTrue();  // +10%
        }

        @Test
        @DisplayName("停損：跌幅剛好等於設定值也算成立")
        void stopLoss() {
            Condition condition = new StopLoss(new BigDecimal("8"));
            assertThat(condition.isSatisfied(holding(series, 2, 0, "100"))).isFalse(); // +10%
            assertThat(condition.isSatisfied(holding(series, 3, 0, "100"))).isTrue();  // -8%
            assertThat(condition.isSatisfied(holding(series, 4, 0, "100"))).isFalse(); // -5%
        }

        @Test
        @DisplayName("持有天數：從買進那天算起滿 N 個交易日")
        void holdingDays() {
            Condition condition = new HoldingDays(3);
            assertThat(condition.isSatisfied(holding(series, 3, 1, "105"))).isFalse(); // 2 天
            assertThat(condition.isSatisfied(holding(series, 4, 1, "105"))).isTrue();  // 3 天
        }

        @Test
        @DisplayName("空手時，賣出條件一律不成立")
        void flatNeverSatisfied() {
            assertThat(new TakeProfit(BigDecimal.ONE).isSatisfied(flat(series, 2))).isFalse();
            assertThat(new StopLoss(BigDecimal.ONE).isSatisfied(flat(series, 3))).isFalse();
            assertThat(new HoldingDays(1).isSatisfied(flat(series, 4))).isFalse();
        }

        @Test
        @DisplayName("參數必須大於 0")
        void invalidParameters() {
            assertThatThrownBy(() -> new TakeProfit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StopLoss(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HoldingDays(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CloseBelowLowestLow(0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("組合條件")
    class CompositeTest {

        private final BarSeries series = TestBars.closes("100", "105", "110", "92");
        private final Condition takeProfit = new TakeProfit(new BigDecimal("10"));
        private final Condition stopLoss = new StopLoss(new BigDecimal("8"));
        private final Condition holdTwoDays = new HoldingDays(2);

        @Test
        @DisplayName("AnyOf：任一個成立就成立，並記得是哪一個")
        void anyOf() {
            AnyOf exit = new AnyOf(List.of(takeProfit, stopLoss));

            assertThat(exit.isSatisfied(holding(series, 1, 0, "100"))).isFalse();
            assertThat(exit.firstSatisfied(holding(series, 2, 0, "100"))).contains(takeProfit);
            assertThat(exit.firstSatisfied(holding(series, 3, 0, "100"))).contains(stopLoss);
            assertThat(exit.describe()).isEqualTo("漲 10% 停利，或跌 8% 停損");
        }

        @Test
        @DisplayName("AllOf：全部成立才成立")
        void allOf() {
            Condition both = new AllOf(List.of(takeProfit, holdTwoDays));

            assertThat(both.isSatisfied(holding(series, 2, 1, "100"))).isFalse(); // 漲 10% 但只持有 1 天
            assertThat(both.isSatisfied(holding(series, 2, 0, "100"))).isTrue();  // 漲 10% 且持有 2 天
        }

        @Test
        @DisplayName("組合條件本身也是 Condition，可以再組合")
        void nested() {
            Condition nested = new AnyOf(List.of(new AllOf(List.of(takeProfit, holdTwoDays)), stopLoss));

            assertThat(nested.isSatisfied(holding(series, 3, 0, "100"))).isTrue();
        }

        @Test
        @DisplayName("組合條件不能是空的")
        void empty() {
            assertThatThrownBy(() -> new AnyOf(List.of())).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
