package com.ezstock.backtest;

import com.ezstock.backtest.cost.CostModel;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
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
 * 模擬「起始日買進、一直持有到最後」。
 *
 * 規則（畫面上也會寫出來，讓使用者知道怎麼算的）：
 * <ol>
 *   <li>起始日之後的第一個交易日，用<b>開盤價</b>買進，能買幾股就買幾股（含手續費不超過本金），可以買零股</li>
 *   <li>除息日當天把現金股利加進現金；選擇再投入時，當天用<b>收盤價</b>買進</li>
 *   <li>每天收盤後記錄一次資產 = 股數 × 收盤價 + 現金</li>
 * </ol>
 * 簡化：股利當天入帳（實際約一個月後發放）、不計股利所得稅與二代健保、期末不賣出。
 *
 * 這個類別不碰資料庫，只吃已經查好的資料，所以可以用單元測試把每一種情況算清楚。
 */
public class BuyAndHoldSimulator {

    private static final Logger log = LoggerFactory.getLogger(BuyAndHoldSimulator.class);

    private final CostModel costModel;

    public BuyAndHoldSimulator(CostModel costModel) {
        this.costModel = costModel;
    }

    /**
     * @param prices    起始日之後的日線，依日期由舊到新；第一筆就是買進日
     * @param dividends 同一段期間的除息紀錄
     */
    public HoldingResult simulate(Stock stock, List<DailyPrice> prices, List<Dividend> dividends,
                                  BigDecimal initialCapital, boolean reinvestDividends) {
        if (prices.isEmpty()) {
            throw new IllegalArgumentException(stock.getStockId() + " 在這段期間沒有價格資料");
        }

        DailyPrice first = prices.getFirst();
        BigDecimal buyPrice = first.getOpenPrice();
        Purchase initial = buyAsMuchAsPossible(stock, buyPrice, initialCapital);
        if (initial.shares() == 0) {
            throw new IllegalArgumentException(String.format("本金 %s 元不足以用 %s 元買進 1 股 %s",
                    initialCapital.toPlainString(), buyPrice.toPlainString(), stock.getStockId()));
        }
        log.info("[回測] {} {} 於 {} 以開盤價 {} 買進 {} 股，手續費 {} 元，剩餘現金 {} 元",
                stock.getStockId(), stock.getName(), first.getTradeDate(), buyPrice,
                initial.shares(), initial.cost(), initialCapital.subtract(initial.total()));

        long shares = initial.shares();
        BigDecimal cash = initialCapital.subtract(initial.total());
        BigDecimal totalCosts = initial.cost();
        BigDecimal totalDividends = BigDecimal.ZERO;

        Map<LocalDate, Dividend> dividendByDate = dividends.stream()
                .collect(Collectors.toMap(Dividend::getExDate, Function.identity(), (a, b) -> a));
        List<EquityPoint> curve = new ArrayList<>(prices.size());

        for (DailyPrice price : prices) {
            Dividend dividend = dividendByDate.get(price.getTradeDate());
            if (dividend != null) {
                BigDecimal received = dividend.getCashDividend().multiply(BigDecimal.valueOf(shares))
                        .setScale(0, RoundingMode.DOWN);
                cash = cash.add(received);
                totalDividends = totalDividends.add(received);
                log.debug("[回測] {} 除息 {}：每股 {} 元 × {} 股 = {} 元",
                        stock.getStockId(), price.getTradeDate(), dividend.getCashDividend(), shares, received);

                if (reinvestDividends) {
                    Purchase more = buyAsMuchAsPossible(stock, price.getClosePrice(), cash);
                    if (more.shares() > 0) {
                        shares += more.shares();
                        cash = cash.subtract(more.total());
                        totalCosts = totalCosts.add(more.cost());
                        log.debug("[回測] {} 股利再投入：以收盤價 {} 買進 {} 股，手續費 {} 元，持股變成 {} 股",
                                stock.getStockId(), price.getClosePrice(), more.shares(), more.cost(), shares);
                    }
                }
            }
            BigDecimal value = price.getClosePrice().multiply(BigDecimal.valueOf(shares)).add(cash)
                    .setScale(0, RoundingMode.HALF_UP);
            curve.add(new EquityPoint(price.getTradeDate(), value));
        }

        PerformanceMetrics metrics = PerformanceMetrics.of(initialCapital, curve);
        BigDecimal finalValue = curve.getLast().value();
        String annualized = metrics.annualizedReturnPercent() == null
                ? "未滿一年不計" : metrics.annualizedReturnPercent() + "%";
        log.info("[回測] {} 結果：期末資產 {} 元，總報酬 {}%，年化 {}，最大回落 {}%，股利 {} 元，成本 {} 元",
                stock.getStockId(), finalValue, metrics.totalReturnPercent(), annualized,
                metrics.maxDrawdownPercent(), totalDividends, totalCosts);

        return new HoldingResult(stock.getStockId(), stock.getName(), first.getTradeDate(), buyPrice,
                initial.shares(), shares, cash, finalValue, totalDividends, totalCosts, metrics, curve);
    }

    /**
     * 用這筆錢在這個價格最多能買幾股（價金 + 手續費不能超過 budget）。
     * 先用「錢 ÷ 價格」估最多幾股，再一股一股往下減，直到付得起手續費為止。
     * 手續費最多只占一點點，通常減幾次就停了。
     */
    private Purchase buyAsMuchAsPossible(Stock stock, BigDecimal price, BigDecimal budget) {
        long shares = budget.divide(price, 0, RoundingMode.DOWN).longValue();
        while (shares > 0) {
            BigDecimal amount = price.multiply(BigDecimal.valueOf(shares));
            BigDecimal cost = costModel.buyCost(amount, stock.getType());
            if (amount.add(cost).compareTo(budget) <= 0) {
                return new Purchase(shares, amount, cost);
            }
            shares--;
        }
        return new Purchase(0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** 一次買進：股數、價金、手續費 */
    private record Purchase(long shares, BigDecimal amount, BigDecimal cost) {
        BigDecimal total() {
            return amount.add(cost);
        }
    }
}
