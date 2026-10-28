package com.ezstock.backtest;

import com.ezstock.backtest.cost.CostModel;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.SplitType;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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
 *   <li>股票分割、面額變更的那天，股數乘上分割比例（例如 1 拆 4 就變 4 倍），價值不變</li>
 *   <li>每天收盤後記錄一次資產 = 股數 × 收盤價 + 現金</li>
 * </ol>
 * 簡化：股利當天入帳（實際約一個月後發放）、不計股利所得稅與二代健保、期末不賣出。
 *
 * 這個類別不碰資料庫，只吃已經查好的資料，所以可以用單元測試把每一種情況算清楚。
 */
public class BuyAndHoldSimulator {

    private static final Logger log = LoggerFactory.getLogger(BuyAndHoldSimulator.class);

    /**
     * 台股每天漲跌幅限制 10%。前後兩天的收盤價差超過 11%（多留一點緩衝），
     * 又不是分割日，通常是減資、合併之類還沒處理的公司行為，回測結果可能失真，要提醒使用者。
     */
    static final BigDecimal ABNORMAL_MOVE_PERCENT = new BigDecimal("11");

    /** 同一檔最多列出幾筆異常，避免警告洗版 */
    static final int MAX_WARNINGS = 3;

    private final CostModel costModel;

    public BuyAndHoldSimulator(CostModel costModel) {
        this.costModel = costModel;
    }

    /**
     * @param prices    起始日之後的日線，依日期由舊到新；第一筆就是買進日
     * @param dividends 同一段期間的除息紀錄
     * @param splits    同一段期間的股票分割與面額變更
     */
    public HoldingResult simulate(Stock stock, List<DailyPrice> prices, List<Dividend> dividends,
                                  List<StockSplit> splits, BigDecimal initialCapital, boolean reinvestDividends) {
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
        // 買進日當天或之前的分割，價格已經是分割後的，不用再調整
        Iterator<StockSplit> pendingSplits = splits.stream()
                .filter(s -> s.getSplitDate().isAfter(first.getTradeDate()))
                .sorted(Comparator.comparing(StockSplit::getSplitDate))
                .iterator();
        StockSplit nextSplit = pendingSplits.hasNext() ? pendingSplits.next() : null;

        List<EquityPoint> curve = new ArrayList<>(prices.size());
        List<String> warnings = new ArrayList<>();
        int abnormalMoves = 0;
        BigDecimal previousClose = null;

        for (DailyPrice price : prices) {
            // 1. 分割：分割日可能剛好停止交易，所以只要「已經到了或過了分割日」就套用
            boolean splitToday = false;
            while (nextSplit != null && !nextSplit.getSplitDate().isAfter(price.getTradeDate())) {
                long before = shares;
                shares = BigDecimal.valueOf(shares).multiply(nextSplit.shareRatio())
                        .setScale(0, RoundingMode.DOWN).longValue();
                splitToday = true;
                log.info("[回測] {} 於 {} {}，比例 {}：股數 {} → {}",
                        stock.getStockId(), price.getTradeDate(),
                        nextSplit.getSplitType() == SplitType.SPLIT ? "股票分割" : "面額變更",
                        nextSplit.shareRatio().stripTrailingZeros().toPlainString(), before, shares);
                nextSplit = pendingSplits.hasNext() ? pendingSplits.next() : null;
            }

            Dividend dividend = dividendByDate.get(price.getTradeDate());

            // 2. 檢查價格異常跳動（除息會讓參考價下降，要先扣掉股利再比較）
            if (previousClose != null && !splitToday) {
                BigDecimal reference = dividend == null
                        ? previousClose : previousClose.subtract(dividend.getCashDividend());
                if (reference.signum() > 0) {
                    BigDecimal movePercent = price.getClosePrice().subtract(reference)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(reference, 2, RoundingMode.HALF_UP);
                    if (movePercent.abs().compareTo(ABNORMAL_MOVE_PERCENT) > 0) {
                        abnormalMoves++;
                        String warning = String.format(
                                "%s %s 在 %s 收盤 %s，比前一個交易日 %s 變動 %s%%，超過台股 10%% 漲跌幅限制，"
                                        + "可能有減資等尚未處理的事件，回測結果可能不準確",
                                stock.getStockId(), stock.getName(), price.getTradeDate(),
                                price.getClosePrice().toPlainString(), previousClose.toPlainString(),
                                movePercent.toPlainString());
                        log.warn("[回測] {}", warning);
                        if (abnormalMoves <= MAX_WARNINGS) {
                            warnings.add(warning);
                        }
                    }
                }
            }
            previousClose = price.getClosePrice();

            // 3. 股利
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
            // 4. 收盤後記錄資產
            BigDecimal value = price.getClosePrice().multiply(BigDecimal.valueOf(shares)).add(cash)
                    .setScale(0, RoundingMode.HALF_UP);
            curve.add(new EquityPoint(price.getTradeDate(), value));
        }

        if (abnormalMoves > MAX_WARNINGS) {
            warnings.add(String.format("%s 另外還有 %d 筆價格異常跳動，詳見系統紀錄",
                    stock.getStockId(), abnormalMoves - MAX_WARNINGS));
        }

        PerformanceMetrics metrics = PerformanceMetrics.of(initialCapital, curve);
        BigDecimal finalValue = curve.getLast().value();
        String annualized = metrics.annualizedReturnPercent() == null
                ? "未滿一年不計" : metrics.annualizedReturnPercent() + "%";
        log.info("[回測] {} 結果：期末資產 {} 元，總報酬 {}%，年化 {}，最大回落 {}%，股利 {} 元，成本 {} 元",
                stock.getStockId(), finalValue, metrics.totalReturnPercent(), annualized,
                metrics.maxDrawdownPercent(), totalDividends, totalCosts);

        return new HoldingResult(stock.getStockId(), stock.getName(), first.getTradeDate(), buyPrice,
                initial.shares(), shares, cash, finalValue, totalDividends, totalCosts, metrics, curve,
                List.copyOf(warnings));
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
