package com.ezstock.backtest.strategy;

import com.ezstock.backtest.EquityPoint;
import com.ezstock.backtest.cost.TaiwanStockCostModel;
import com.ezstock.backtest.cost.ZeroCostModel;
import com.ezstock.backtest.strategy.condition.AnyOf;
import com.ezstock.backtest.strategy.condition.CloseBelowLowestLow;
import com.ezstock.backtest.strategy.condition.Condition;
import com.ezstock.backtest.strategy.condition.HoldingDays;
import com.ezstock.backtest.strategy.condition.StopLoss;
import com.ezstock.backtest.strategy.condition.TakeProfit;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.SplitType;
import com.ezstock.model.StockSplit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.ezstock.backtest.strategy.TestBars.DAY1;
import static com.ezstock.backtest.strategy.TestBars.STOCK;
import static org.assertj.core.api.Assertions.assertThat;

class StrategySimulatorTest {

    private static final BigDecimal CAPITAL = new BigDecimal("100000");

    /** 破 2 日低點買進 */
    private final Condition entry = new CloseBelowLowestLow(2);

    /** 漲 10% 停利，或跌 5% 停損 */
    private final Condition exit = new AnyOf(List.of(
            new TakeProfit(new BigDecimal("10")), new StopLoss(new BigDecimal("5"))));

    private final StrategySimulator noCost = new StrategySimulator(new ZeroCostModel());

    /**
     * 第 2 天收盤 95 跌破前 2 日低點 100 → 第 3 天開盤 96 買進
     * 第 4 天收盤 106，比買進價 96 漲 10.4% → 第 5 天開盤 107 賣出
     */
    private final List<DailyPrice> winningTrade = TestBars.ohlcList(
            "100,100,100,100",
            "100,100,100,100",
            "100,100,95,95",
            "96,100,96,100",
            "100,106,100,106",
            "107,108,107,108");

    @Test
    @DisplayName("收盤出現訊號，隔天開盤才成交，並記錄賣出原因")
    void tradesAtNextOpen() {
        StrategyResult result = noCost.simulate(STOCK, winningTrade, List.of(), List.of(), entry, exit, CAPITAL);

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.entryDate()).isEqualTo(DAY1.plusDays(3));
            assertThat(trade.entryPrice()).isEqualByComparingTo("96");
            assertThat(trade.exitDate()).isEqualTo(DAY1.plusDays(5));
            assertThat(trade.exitPrice()).isEqualByComparingTo("107");
            // 100,000 ÷ 96 = 1041 股，賣 1041 × 107 = 111,387，成本 99,936，賺 11,451
            assertThat(trade.shares()).isEqualTo(1041);
            assertThat(trade.profit()).isEqualByComparingTo("11451");
            assertThat(trade.returnPercent()).isEqualByComparingTo("11.46");
            assertThat(trade.holdingDays()).isEqualTo(2);
            assertThat(trade.exitReason()).isEqualTo("漲 10% 停利");
        });
        assertThat(result.finalValue()).isEqualByComparingTo("111451");
        assertThat(result.winRatePercent()).isEqualByComparingTo("100");
        // 6 天裡有 2 天收盤時持有股票
        assertThat(result.exposurePercent()).isEqualByComparingTo("33.33");
        assertThat(result.equityCurve()).extracting(EquityPoint::value)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"),
                        new BigDecimal("104164"), new BigDecimal("110410"), new BigDecimal("111451"));
    }

    @Test
    @DisplayName("交易成本：買進付手續費，賣出付手續費加證交稅")
    void withCosts() {
        StrategySimulator taiwan = new StrategySimulator(new TaiwanStockCostModel());

        StrategyResult result = taiwan.simulate(STOCK, winningTrade, List.of(), List.of(), entry, exit, CAPITAL);

        // 買 1040 股 × 96 = 99,840，手續費 142
        // 賣 1040 股 × 107 = 111,280，手續費 158 + 證交稅 333 = 491
        assertThat(result.trades().getFirst().shares()).isEqualTo(1040);
        assertThat(result.totalCosts()).isEqualByComparingTo("633");
    }

    @Test
    @DisplayName("回測結束還沒賣：記成持有中的交易，不算進勝率")
    void openAtEnd() {
        Condition neverExit = new HoldingDays(100);

        StrategyResult result = noCost.simulate(STOCK, winningTrade, List.of(), List.of(), entry, neverExit, CAPITAL);

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.isOpen()).isTrue();
            assertThat(trade.exitReason()).isEqualTo("回測結束時仍持有");
        });
        assertThat(result.winRatePercent()).isNull();
    }

    @Test
    @DisplayName("最後一天收盤出現訊號：提醒使用者隔天開盤會執行")
    void pendingSignalOnLastDay() {
        List<DailyPrice> prices = TestBars.ohlcList("100,100,100,100", "100,100,100,100", "100,100,95,95");

        StrategyResult result = noCost.simulate(STOCK, prices, List.of(), List.of(), entry, exit, CAPITAL);

        assertThat(result.trades()).isEmpty();
        assertThat(result.pendingSignal()).contains("買進訊號").contains("破前 2 日最低價");
    }

    @Test
    @DisplayName("持有中遇到 1 拆 4：股數變 4 倍、買進價變 1/4，不會誤觸停損")
    void splitWhileHolding() {
        List<DailyPrice> prices = TestBars.ohlcList(
                "100,100,100,100",
                "100,100,100,100",
                "100,100,95,95",
                "96,100,96,100",
                "24.5,24.5,24.5,24.5");   // 分割後：比調整後的買進價 24 還高，沒有虧
        StockSplit split = new StockSplit(STOCK, DAY1.plusDays(4), SplitType.SPLIT,
                new BigDecimal("100"), new BigDecimal("25"));

        StrategyResult result = noCost.simulate(STOCK, prices, List.of(), List.of(split), entry, exit, CAPITAL);

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.isOpen()).isTrue();
            assertThat(trade.shares()).isEqualTo(4164);
        });
        assertThat(result.pendingSignal()).isNull();
        // 現金 64 + 4164 股 × 24.5
        assertThat(result.finalValue()).isEqualByComparingTo("102082");
    }

    @Test
    @DisplayName("股利：持有中才領得到，並算進那筆交易的損益")
    void dividendsWhileHolding() {
        List<Dividend> dividends = List.of(
                new Dividend(STOCK, DAY1.plusDays(1), new BigDecimal("3")),   // 還沒買，領不到
                new Dividend(STOCK, DAY1.plusDays(4), new BigDecimal("2")));  // 持有中，1041 × 2

        StrategyResult result = noCost.simulate(STOCK, winningTrade, dividends, List.of(), entry, exit, CAPITAL);

        assertThat(result.totalDividends()).isEqualByComparingTo("2082");
        assertThat(result.trades().getFirst().profit()).isEqualByComparingTo("13533");
    }
}
