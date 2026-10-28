package com.ezstock.backtest.strategy;

import com.ezstock.backtest.strategy.condition.AllOf;
import com.ezstock.backtest.strategy.condition.AnyOf;
import com.ezstock.backtest.strategy.condition.CloseBelowLowestLow;
import com.ezstock.backtest.strategy.condition.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionFactoryTest {

    private static ConditionRule rule(ConditionType type, String value) {
        return new ConditionRule(type, new BigDecimal(value));
    }

    @ParameterizedTest
    @EnumSource(ConditionType.class)
    @DisplayName("每一種條件用預設值都建立得出來")
    void everyTypeCanBeCreated(ConditionType type) {
        Condition condition = type.create(BigDecimal.valueOf(type.defaultValue()));

        assertThat(condition.describe()).isNotBlank();
    }

    @Test
    @DisplayName("只有一個買進條件時直接回傳它；多個時包成 AllOf")
    void entry() {
        Condition single = ConditionFactory.entry(List.of(rule(ConditionType.CLOSE_BELOW_LOWEST_LOW, "5")));
        assertThat(single).isInstanceOf(CloseBelowLowestLow.class);
        assertThat(single.describe()).isEqualTo("收盤跌破前 5 日最低價");

        Condition both = ConditionFactory.entry(List.of(
                rule(ConditionType.CLOSE_ABOVE_MOVING_AVERAGE, "20"),
                rule(ConditionType.CLOSE_ABOVE_HIGHEST_HIGH, "10")));
        assertThat(both).isInstanceOf(AllOf.class);
        assertThat(both.describe()).isEqualTo("收盤站上 20 日均線，而且收盤突破前 10 日最高價");
    }

    @Test
    @DisplayName("賣出條件一律包成 AnyOf")
    void exit() {
        AnyOf exit = ConditionFactory.exit(List.of(
                rule(ConditionType.TAKE_PROFIT, "10"), rule(ConditionType.STOP_LOSS, "5")));

        assertThat(exit.conditions()).hasSize(2);
        assertThat(exit.describe()).isEqualTo("漲 10% 停利，或跌 5% 停損");
    }

    @Test
    @DisplayName("停利、停損、持有天數不能當買進條件")
    void exitOnlyTypes() {
        assertThatThrownBy(() -> ConditionFactory.entry(List.of(rule(ConditionType.TAKE_PROFIT, "10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能當賣出條件");
    }

    @Test
    @DisplayName("條件數量：至少 1 個、最多 3 個")
    void ruleCount() {
        assertThatThrownBy(() -> ConditionFactory.entry(List.of()))
                .hasMessageContaining("至少設定 1 個買進條件");
        assertThatThrownBy(() -> ConditionFactory.exit(List.of(
                rule(ConditionType.TAKE_PROFIT, "10"), rule(ConditionType.STOP_LOSS, "5"),
                rule(ConditionType.HOLDING_DAYS, "20"), rule(ConditionType.CLOSE_BELOW_MOVING_AVERAGE, "10"))))
                .hasMessageContaining("最多 3 個");
    }

    @Test
    @DisplayName("數值超出範圍、或天數不是整數，丟出 IllegalArgumentException")
    void invalidValues() {
        assertThatThrownBy(() -> ConditionType.CLOSE_BELOW_LOWEST_LOW.create(new BigDecimal("0")))
                .hasMessageContaining("1～240 日");
        assertThatThrownBy(() -> ConditionType.HOLDING_DAYS.create(new BigDecimal("500")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConditionType.CLOSE_ABOVE_MOVING_AVERAGE.create(new BigDecimal("2.5")))
                .hasMessageContaining("必須是整數");
        assertThatThrownBy(() -> ConditionType.STOP_LOSS.create(new BigDecimal("150")))
                .hasMessageContaining("0.1～100 %");
        assertThatThrownBy(() -> ConditionType.TAKE_PROFIT.create(null))
                .hasMessageContaining("未填");
    }

    @Test
    @DisplayName("百分比可以有小數，天數寫成 5.0 也可以")
    void decimals() {
        assertThat(ConditionType.STOP_LOSS.create(new BigDecimal("2.5")).describe()).isEqualTo("跌 2.5% 停損");
        assertThat(ConditionType.CLOSE_BELOW_LOWEST_LOW.create(new BigDecimal("5.0")).describe())
                .isEqualTo("收盤跌破前 5 日最低價");
    }
}
