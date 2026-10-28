package com.ezstock.backtest;

import com.ezstock.backtest.cost.TaiwanStockCostModel;
import com.ezstock.backtest.cost.ZeroCostModel;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.SplitType;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyAndHoldSimulatorTest {

    private static final BigDecimal CAPITAL = new BigDecimal("100000");
    private static final LocalDate DAY1 = LocalDate.of(2025, 1, 2);

    private final Stock stock = new Stock("TEST", "測試股", StockType.STOCK, "測試");
    private final BuyAndHoldSimulator noCost = new BuyAndHoldSimulator(new ZeroCostModel());

    @Test
    @DisplayName("開盤價買進，期末資產 = 股數 × 最後收盤 + 現金")
    void basic() {
        HoldingResult result = noCost.simulate(stock, prices("100", "105", "110"), List.of(), List.of(), CAPITAL, false);

        assertThat(result.buyPrice()).isEqualByComparingTo("100");
        assertThat(result.initialShares()).isEqualTo(1000);
        assertThat(result.finalValue()).isEqualByComparingTo("110000");
        assertThat(result.metrics().totalReturnPercent()).isEqualByComparingTo("10.00");
        assertThat(result.equityCurve()).extracting(EquityPoint::value)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100000"), new BigDecimal("105000"), new BigDecimal("110000"));
    }

    @Test
    @DisplayName("含手續費：價金 + 手續費不能超過本金，零頭留在現金")
    void withFee() {
        BuyAndHoldSimulator taiwan = new BuyAndHoldSimulator(new TaiwanStockCostModel());

        HoldingResult result = taiwan.simulate(stock, prices("100"), List.of(), List.of(), CAPITAL, false);

        // 999 股 = 99,900 + 手續費 142 = 100,042 超過本金；998 股 = 99,800 + 142 = 99,942
        assertThat(result.initialShares()).isEqualTo(998);
        assertThat(result.totalCosts()).isEqualByComparingTo("142");
        assertThat(result.cash()).isEqualByComparingTo("58");
    }

    @Test
    @DisplayName("股利不再投入：除息日現金增加，股數不變")
    void dividendAsCash() {
        HoldingResult result = noCost.simulate(stock, prices("100", "95", "96"),
                List.of(dividend(DAY1.plusDays(1), "5")), List.of(), CAPITAL, false);

        assertThat(result.totalDividends()).isEqualByComparingTo("5000");
        assertThat(result.finalShares()).isEqualTo(1000);
        // 1000 × 96 + 5000
        assertThat(result.finalValue()).isEqualByComparingTo("101000");
    }

    @Test
    @DisplayName("股利再投入：除息日用收盤價把股利買成股票")
    void dividendReinvested() {
        HoldingResult result = noCost.simulate(stock, prices("100", "100", "110"),
                List.of(dividend(DAY1.plusDays(1), "5")), List.of(), CAPITAL, true);

        // 5000 元 ÷ 100 = 50 股
        assertThat(result.finalShares()).isEqualTo(1050);
        assertThat(result.cash()).isEqualByComparingTo("0");
        assertThat(result.finalValue()).isEqualByComparingTo("115500");
    }

    @Test
    @DisplayName("最大回落：從最高點 120 跌到 90 是 -25%")
    void maxDrawdown() {
        HoldingResult result = noCost.simulate(stock, prices("100", "120", "90", "130"), List.of(), List.of(), CAPITAL, false);

        assertThat(result.metrics().maxDrawdownPercent()).isEqualByComparingTo("-25.00");
    }

    @Test
    @DisplayName("年化報酬：剛好一年翻倍是 100%")
    void annualized() {
        List<DailyPrice> list = List.of(
                price(LocalDate.of(2025, 1, 2), "100", "100"),
                price(LocalDate.of(2026, 1, 2), "200", "200"));

        HoldingResult result = noCost.simulate(stock, list, List.of(), List.of(), CAPITAL, false);

        assertThat(result.metrics().totalReturnPercent()).isEqualByComparingTo("100.00");
        assertThat(result.metrics().annualizedReturnPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("年化報酬：未滿一年不計算，避免 3 天賺 10% 被換算成幾億 %")
    void noAnnualizedUnderOneYear() {
        HoldingResult result = noCost.simulate(stock, prices("100", "105", "110"), List.of(), List.of(), CAPITAL, false);

        assertThat(result.metrics().totalReturnPercent()).isEqualByComparingTo("10.00");
        assertThat(result.metrics().annualizedReturnPercent()).isNull();
    }

    @Test
    @DisplayName("股票分割：1 拆 4 當天股數變 4 倍，資產不會因為價格變 1/4 而暴跌")
    void split() {
        List<DailyPrice> list = List.of(
                price(DAY1, "200", "200"),
                price(DAY1.plusDays(1), "200", "200"),
                price(DAY1.plusDays(7), "50", "51"));
        StockSplit split = new StockSplit(stock, DAY1.plusDays(7), SplitType.SPLIT,
                new BigDecimal("200"), new BigDecimal("50"));

        HoldingResult result = noCost.simulate(stock, list, List.of(), List.of(split), CAPITAL, false);

        assertThat(result.initialShares()).isEqualTo(500);
        assertThat(result.finalShares()).isEqualTo(2000);
        // 2000 股 × 51
        assertThat(result.finalValue()).isEqualByComparingTo("102000");
        assertThat(result.metrics().maxDrawdownPercent()).isEqualByComparingTo("0");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("沒有分割資料卻跳動超過 11%，要產生警告；除息造成的下跌不算")
    void abnormalMoveWarning() {
        HoldingResult result = noCost.simulate(stock, prices("100", "105", "50", "52"),
                List.of(), List.of(), CAPITAL, false);

        assertThat(result.warnings()).singleElement().asString()
                .contains(DAY1.plusDays(2).toString())
                .contains("-52.38%");

        // 前一天 100，除息 8 元後參考價 92，收盤 90 只跌 2.17%，不算異常
        HoldingResult exDividend = noCost.simulate(stock, prices("100", "90"),
                List.of(dividend(DAY1.plusDays(1), "8")), List.of(), CAPITAL, false);
        assertThat(exDividend.warnings()).isEmpty();
    }

    @Test
    @DisplayName("沒有價格、或本金買不起 1 股，丟出 IllegalArgumentException")
    void invalid() {
        assertThatThrownBy(() -> noCost.simulate(stock, List.of(), List.of(), List.of(), CAPITAL, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noCost.simulate(stock, prices("2000"), List.of(), List.of(), new BigDecimal("1000"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不足以");
    }

    /** 連續幾天的價格，開盤 = 收盤 = 給的數字 */
    private List<DailyPrice> prices(String... closes) {
        List<DailyPrice> list = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            list.add(price(DAY1.plusDays(i), closes[i], closes[i]));
        }
        return list;
    }

    private DailyPrice price(LocalDate date, String open, String close) {
        BigDecimal o = new BigDecimal(open);
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(stock, date, o, o.max(c), o.min(c), c, 1_000L);
    }

    private Dividend dividend(LocalDate exDate, String cash) {
        return new Dividend(stock, exDate, new BigDecimal(cash));
    }
}
