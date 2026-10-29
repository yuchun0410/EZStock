package com.ezstock.backtest.strategy;

import com.ezstock.backtest.EquityPoint;
import com.ezstock.backtest.PerformanceMetrics;
import com.ezstock.backtest.cost.CostModel;
import com.ezstock.backtest.strategy.condition.AnyOf;
import com.ezstock.backtest.strategy.condition.Condition;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 條件回測：符合買進條件就買、符合賣出條件就賣，同一時間最多持有一筆。
 *
 * 規則：
 * <ol>
 *   <li>每天<b>收盤後</b>判斷條件。收盤那一刻才知道「今天跌破了」，不可能用今天的價格成交</li>
 *   <li>條件成立的話，<b>隔天開盤價</b>買進或賣出（避免偷看未來的資料）</li>
 *   <li>空手時只看買進條件；持有時只看賣出條件</li>
 *   <li>每次買進都用手上全部的現金，能買幾股就買幾股（可以買零股）</li>
 *   <li>持有期間領到的股利：選擇再投入的話，除息日用收盤價加碼買進；否則存成現金，下次買進時一起用</li>
 *   <li>持有期間遇到股票分割，股數和買進價都依比例調整</li>
 * </ol>
 *
 * 跟 {@link com.ezstock.backtest.BuyAndHoldSimulator} 一樣不碰資料庫，所以每種情況都能用單元測試算清楚。
 */
public class StrategySimulator {

    private static final Logger log = LoggerFactory.getLogger(StrategySimulator.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CostModel costModel;

    public StrategySimulator(CostModel costModel) {
        this.costModel = costModel;
    }

    /** 從第一根 K 棒就開始交易，股利存成現金 */
    public StrategyResult simulate(Stock stock, List<DailyPrice> prices, List<Dividend> dividends,
                                   List<StockSplit> splits, Condition entry, Condition exit,
                                   BigDecimal initialCapital) {
        return simulate(stock, prices, dividends, splits, entry, exit, initialCapital, null, false);
    }

    /**
     * @param tradeFrom 從哪一天開始交易。prices 可以包含更早的資料，那些只拿來算指標（暖機），
     *                  例如年線要 240 天的資料，不往前多抓的話，回測的第一年都不會有訊號。
     *                  null 代表從第一根 K 棒就開始
     * @param reinvestDividends 持有中領到股利時，是否在除息日用收盤價加碼買進
     */
    public StrategyResult simulate(Stock stock, List<DailyPrice> prices, List<Dividend> dividends,
                                   List<StockSplit> splits, Condition entry, Condition exit,
                                   BigDecimal initialCapital, LocalDate tradeFrom, boolean reinvestDividends) {
        if (prices.isEmpty()) {
            throw new IllegalArgumentException(stock.getStockId() + " 在這段期間沒有價格資料");
        }
        String stockId = stock.getStockId();
        BarSeries series = new BarSeries(prices);
        int startIndex = 0;
        if (tradeFrom != null) {
            while (startIndex < series.size() && series.date(startIndex).isBefore(tradeFrom)) {
                startIndex++;
            }
            if (startIndex == series.size()) {
                throw new IllegalArgumentException(stockId + " 在 " + tradeFrom + " 之後沒有價格資料");
            }
        }
        Map<LocalDate, Dividend> dividendByDate = dividends.stream()
                .collect(Collectors.toMap(Dividend::getExDate, Function.identity(), (a, b) -> a));
        Map<LocalDate, StockSplit> splitByDate = splits.stream()
                .collect(Collectors.toMap(StockSplit::getSplitDate, Function.identity(), (a, b) -> a));

        log.info("[條件回測] {} {} 開始：{} ~ {}（前面 {} 根 K 棒只用來計算指標），本金 {} 元；買進：{}；賣出：{}",
                stockId, stock.getName(), series.date(startIndex), series.date(series.size() - 1), startIndex,
                initialCapital.toPlainString(), entry.describe(), exit.describe());

        BigDecimal cash = initialCapital;
        long shares = 0;
        OpenPosition position = null;
        BigDecimal positionCost = BigDecimal.ZERO;       // 這筆部位的買進總成本（含手續費）
        BigDecimal positionDividends = BigDecimal.ZERO;  // 這筆部位持有期間領到的股利
        BigDecimal totalCosts = BigDecimal.ZERO;
        BigDecimal totalDividends = BigDecimal.ZERO;
        Signal pending = null;
        int daysInMarket = 0;

        List<Trade> trades = new ArrayList<>();
        List<EquityPoint> curve = new ArrayList<>(series.size() - startIndex);

        for (int i = startIndex; i < series.size(); i++) {
            LocalDate date = series.date(i);

            // 1. 股票分割：持有中的話，股數變多、買進價等比例變低，停利停損才不會誤判
            StockSplit split = splitByDate.get(date);
            if (split != null && position != null) {
                BigDecimal ratio = split.shareRatio();
                long before = shares;
                shares = BigDecimal.valueOf(shares).multiply(ratio).setScale(0, RoundingMode.DOWN).longValue();
                position = new OpenPosition(position.entryIndex(),
                        position.entryPrice().divide(ratio, 4, RoundingMode.HALF_UP));
                log.info("[條件回測] {} {} 股票分割，比例 {}：股數 {} → {}",
                        stockId, date, ratio.stripTrailingZeros().toPlainString(), before, shares);
            }

            // 2. 執行昨天收盤出現的訊號：今天開盤成交
            if (pending != null) {
                BigDecimal open = series.open(i);
                if (pending.type() == SignalType.BUY) {
                    long buyShares = maxShares(stock, open, cash);
                    if (buyShares > 0) {
                        BigDecimal amount = open.multiply(BigDecimal.valueOf(buyShares));
                        BigDecimal fee = costModel.buyCost(amount, stock.getType());
                        cash = cash.subtract(amount).subtract(fee);
                        shares = buyShares;
                        position = new OpenPosition(i, open);
                        positionCost = amount.add(fee);
                        positionDividends = BigDecimal.ZERO;
                        totalCosts = totalCosts.add(fee);
                        log.info("[條件回測] {} {} 開盤 {} 買進 {} 股，手續費 {} 元（訊號：{}）",
                                stockId, date, open, buyShares, fee, pending.reason());
                    } else {
                        log.warn("[條件回測] {} {} 現金 {} 元不足以用 {} 元買進 1 股，略過這次買進",
                                stockId, date, cash, open);
                    }
                } else {
                    BigDecimal amount = open.multiply(BigDecimal.valueOf(shares));
                    BigDecimal cost = costModel.sellCost(amount, stock.getType());
                    BigDecimal proceeds = amount.subtract(cost);
                    cash = cash.add(proceeds);
                    totalCosts = totalCosts.add(cost);
                    Trade trade = closeTrade(series, position, i, open, shares,
                            proceeds, positionCost, positionDividends, pending.reason());
                    trades.add(trade);
                    log.info("[條件回測] {} {} 開盤 {} 賣出 {} 股，成本 {} 元，這筆損益 {} 元（{}%），原因：{}",
                            stockId, date, open, shares, cost, trade.profit(), trade.returnPercent(), pending.reason());
                    shares = 0;
                    position = null;
                }
                pending = null;
            }

            // 3. 除息：持有中才領得到
            Dividend dividend = dividendByDate.get(date);
            if (dividend != null && shares > 0) {
                BigDecimal received = dividend.getCashDividend().multiply(BigDecimal.valueOf(shares))
                        .setScale(0, RoundingMode.DOWN);
                cash = cash.add(received);
                positionDividends = positionDividends.add(received);
                totalDividends = totalDividends.add(received);
                log.debug("[條件回測] {} {} 除息，領到 {} 元", stockId, date, received);

                if (reinvestDividends) {
                    BigDecimal close = series.close(i);
                    long more = maxShares(stock, close, cash);
                    if (more > 0) {
                        BigDecimal amount = close.multiply(BigDecimal.valueOf(more));
                        BigDecimal fee = costModel.buyCost(amount, stock.getType());
                        cash = cash.subtract(amount).subtract(fee);
                        shares += more;
                        // 加碼的錢算進這筆部位的成本，賣出時損益才會正確
                        positionCost = positionCost.add(amount).add(fee);
                        totalCosts = totalCosts.add(fee);
                        log.debug("[條件回測] {} {} 股利再投入：以收盤價 {} 加碼 {} 股，持股變成 {} 股",
                                stockId, date, close, more, shares);
                    }
                }
            }

            // 4. 收盤後判斷條件，成立的話留到明天開盤執行
            ConditionContext context = new ConditionContext(series, i, position);
            if (position != null) {
                String reason = exitReason(exit, context);
                if (reason != null) {
                    pending = new Signal(SignalType.SELL, reason);
                    log.debug("[條件回測] {} {} 收盤 {} 出現賣出訊號：{}", stockId, date, series.close(i), reason);
                }
            } else if (entry.isSatisfied(context)) {
                pending = new Signal(SignalType.BUY, entry.describe());
                log.debug("[條件回測] {} {} 收盤 {} 出現買進訊號：{}", stockId, date, series.close(i), entry.describe());
            }

            // 5. 記錄當天收盤後的資產
            if (shares > 0) {
                daysInMarket++;
            }
            BigDecimal value = series.close(i).multiply(BigDecimal.valueOf(shares)).add(cash)
                    .setScale(0, RoundingMode.HALF_UP);
            curve.add(new EquityPoint(date, value));
        }

        // 回測結束還持有：用最後收盤價估值，記成一筆「持有中」的交易（不計賣出成本）
        if (position != null) {
            int last = series.size() - 1;
            BigDecimal marketValue = series.close(last).multiply(BigDecimal.valueOf(shares));
            Trade open = closeTrade(series, position, last, series.close(last), shares,
                    marketValue, positionCost, positionDividends, "回測結束時仍持有");
            trades.add(new Trade(open.entryDate(), open.entryPrice(), null, null, open.shares(),
                    open.profit(), open.returnPercent(), open.holdingDays(), open.exitReason()));
        }

        String pendingSignal = pending == null ? null
                : (pending.type() == SignalType.BUY ? "最後一天收盤出現買進訊號：" : "最後一天收盤出現賣出訊號：")
                + pending.reason() + "（隔天開盤執行）";

        StrategyResult result = buildResult(stock, initialCapital, curve, trades,
                totalDividends, totalCosts, daysInMarket, pendingSignal);
        log.info("[條件回測] {} 結果：期末資產 {} 元，總報酬 {}%，交易 {} 筆，勝率 {}，最大回落 {}%，在市場 {}% 的時間",
                stockId, result.finalValue(), result.metrics().totalReturnPercent(), result.tradeCount(),
                result.winRatePercent() == null ? "-" : result.winRatePercent() + "%",
                result.metrics().maxDrawdownPercent(), result.exposurePercent());
        return result;
    }

    /** 賣出條件是 AnyOf 的話，記錄是哪一個條件成立；否則用整個條件的說明 */
    private String exitReason(Condition exit, ConditionContext context) {
        if (exit instanceof AnyOf anyOf) {
            return anyOf.firstSatisfied(context).map(Condition::describe).orElse(null);
        }
        return exit.isSatisfied(context) ? exit.describe() : null;
    }

    private Trade closeTrade(BarSeries series, OpenPosition position, int exitIndex, BigDecimal exitPrice,
                             long shares, BigDecimal proceeds, BigDecimal cost, BigDecimal dividends,
                             String reason) {
        BigDecimal profit = proceeds.add(dividends).subtract(cost).setScale(0, RoundingMode.HALF_UP);
        BigDecimal returnPercent = profit.multiply(HUNDRED).divide(cost, 2, RoundingMode.HALF_UP);
        return new Trade(series.date(position.entryIndex()), series.open(position.entryIndex()),
                series.date(exitIndex), exitPrice, shares, profit, returnPercent,
                exitIndex - position.entryIndex(), reason);
    }

    /** 價金 + 手續費不超過 budget 的最多股數（跟 BuyAndHoldSimulator 同樣的算法） */
    private long maxShares(Stock stock, BigDecimal price, BigDecimal budget) {
        long shares = budget.divide(price, 0, RoundingMode.DOWN).longValue();
        while (shares > 0) {
            BigDecimal amount = price.multiply(BigDecimal.valueOf(shares));
            if (amount.add(costModel.buyCost(amount, stock.getType())).compareTo(budget) <= 0) {
                return shares;
            }
            shares--;
        }
        return 0;
    }

    private StrategyResult buildResult(Stock stock, BigDecimal initialCapital, List<EquityPoint> curve,
                                       List<Trade> trades, BigDecimal totalDividends, BigDecimal totalCosts,
                                       int daysInMarket, String pendingSignal) {
        List<Trade> closed = trades.stream().filter(t -> !t.isOpen()).toList();
        BigDecimal winRate = null;
        BigDecimal averageReturn = null;
        if (!closed.isEmpty()) {
            long wins = closed.stream().filter(Trade::isWin).count();
            winRate = BigDecimal.valueOf(wins * 100).divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP);
            averageReturn = closed.stream().map(Trade::returnPercent).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP);
        }
        BigDecimal exposure = BigDecimal.valueOf(daysInMarket * 100L)
                .divide(BigDecimal.valueOf(curve.size()), 2, RoundingMode.HALF_UP);

        return new StrategyResult(stock.getStockId(), stock.getName(), curve.getLast().value(),
                totalDividends, totalCosts, PerformanceMetrics.of(initialCapital, curve),
                trades.size(), winRate, averageReturn, exposure, List.copyOf(trades), curve, pendingSignal);
    }

    private enum SignalType { BUY, SELL }

    /** 收盤後出現的訊號，隔天開盤執行 */
    private record Signal(SignalType type, String reason) {
    }
}
