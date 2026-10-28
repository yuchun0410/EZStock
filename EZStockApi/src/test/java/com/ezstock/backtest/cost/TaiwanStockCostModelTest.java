package com.ezstock.backtest.cost;

import com.ezstock.model.StockType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaiwanStockCostModelTest {

    private final CostModel full = new TaiwanStockCostModel();

    @Test
    @DisplayName("買進：手續費 0.1425%，小數無條件捨去")
    void buyFee() {
        // 100,000 × 0.1425% = 142.5 → 142
        assertThat(full.buyCost(new BigDecimal("100000"), StockType.STOCK)).isEqualByComparingTo("142");
    }

    @Test
    @DisplayName("買進：手續費不足 20 元以 20 元計")
    void minFee() {
        // 10,000 × 0.1425% = 14.25 → 未滿 20，收 20
        assertThat(full.buyCost(new BigDecimal("10000"), StockType.STOCK)).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("賣出：股票是手續費 + 0.3% 證交稅，ETF 是手續費 + 0.1%")
    void sellTax() {
        BigDecimal amount = new BigDecimal("100000");
        // 142 + 300
        assertThat(full.sellCost(amount, StockType.STOCK)).isEqualByComparingTo("442");
        // 142 + 100
        assertThat(full.sellCost(amount, StockType.ETF)).isEqualByComparingTo("242");
    }

    @Test
    @DisplayName("手續費折扣：6 折")
    void discount() {
        CostModel discounted = new TaiwanStockCostModel(new BigDecimal("0.6"));
        // 100,000 × 0.1425% × 0.6 = 85.5 → 85
        assertThat(discounted.buyCost(new BigDecimal("100000"), StockType.STOCK)).isEqualByComparingTo("85");
        assertThat(discounted.describe()).contains("6 折");
    }

    @Test
    @DisplayName("折扣必須大於 0 且不超過 1")
    void invalidDiscount() {
        assertThatThrownBy(() -> new TaiwanStockCostModel(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaiwanStockCostModel(new BigDecimal("1.5"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不計成本：買賣都是 0")
    void zeroCost() {
        CostModel zero = new ZeroCostModel();
        assertThat(zero.buyCost(new BigDecimal("100000"), StockType.STOCK)).isEqualByComparingTo("0");
        assertThat(zero.sellCost(new BigDecimal("100000"), StockType.ETF)).isEqualByComparingTo("0");
    }
}
