package com.ezstock.service.impl;

import com.ezstock.backtest.BuyAndHoldSimulator;
import com.ezstock.backtest.HoldingResult;
import com.ezstock.backtest.cost.CostModel;
import com.ezstock.backtest.strategy.ConditionFactory;
import com.ezstock.backtest.strategy.ConditionRule;
import com.ezstock.backtest.strategy.ConditionType;
import com.ezstock.backtest.strategy.StrategyResult;
import com.ezstock.backtest.strategy.StrategySimulator;
import com.ezstock.backtest.strategy.condition.AnyOf;
import com.ezstock.backtest.strategy.condition.Condition;
import com.ezstock.dto.ConditionSpec;
import com.ezstock.dto.ConditionTypeInfo;
import com.ezstock.dto.StrategyRequest;
import com.ezstock.dto.StrategyResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.DailyPrice;
import com.ezstock.service.StrategyBacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 條件回測：把表單上的規則交給 {@link ConditionFactory} 變成 Condition，
 * 用 {@link BacktestDataLoader} 準備資料，再交給 {@link StrategySimulator} 計算。
 * 同時用 {@link BuyAndHoldSimulator} 算「一直持有」當對照組。
 */
@Service
public class StrategyBacktestServiceImpl implements StrategyBacktestService {

    private static final Logger log = LoggerFactory.getLogger(StrategyBacktestServiceImpl.class);

    private final BacktestDataLoader dataLoader;

    public StrategyBacktestServiceImpl(BacktestDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    @Override
    public List<ConditionTypeInfo> conditionTypes() {
        return Arrays.stream(ConditionType.values()).map(ConditionTypeInfo::from).toList();
    }

    @Override
    public StrategyResponse run(StrategyRequest request) {
        long started = System.currentTimeMillis();
        String stockId = request.stockId().trim();

        // 先建立條件：規則有錯的話，不用去撈資料就能直接回 400
        Condition entry;
        AnyOf exit;
        try {
            entry = ConditionFactory.entry(toRules(request.entryConditions()));
            exit = ConditionFactory.exit(toRules(request.exitConditions()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        BacktestDataLoader.Period period = dataLoader.resolvePeriod(request.startDate(), request.endDate());
        int lookback = Math.max(ConditionFactory.lookbackDays(toRules(request.entryConditions())),
                ConditionFactory.lookbackDays(toRules(request.exitConditions())));
        LocalDate loadFrom = period.start().minusDays(ConditionFactory.warmupCalendarDays(lookback));
        BacktestDataLoader.StockData data = dataLoader.load(stockId,
                new BacktestDataLoader.Period(loadFrom, period.end()));
        // 對照組「一直持有」從起始日開始，不包含暖機的資料
        List<DailyPrice> periodPrices = data.prices().stream()
                .filter(price -> !price.getTradeDate().isBefore(period.start()))
                .toList();
        if (periodPrices.isEmpty()) {
            throw new BadRequestException(stockId + " 在 " + period.start() + " ~ " + period.end() + " 沒有價格資料");
        }
        log.debug("[條件回測] {} 條件最多需要 {} 天的歷史資料，從 {} 開始撈，暖機 {} 根 K 棒",
                stockId, lookback, loadFrom, data.prices().size() - periodPrices.size());
        CostModel costModel = BacktestDataLoader.costModel(request.includeCosts(), request.feeDiscount());
        boolean reinvest = request.reinvestDividends() == null || request.reinvestDividends();
        log.info("[條件回測] 開始：{} {}，期間 {} ~ {}，本金 {} 元，買進「{}」，賣出「{}」，股利{}，{}",
                stockId, data.stock().getName(), period.start(), period.end(),
                request.initialCapital().toPlainString(), entry.describe(), exit.describe(),
                reinvest ? "再投入" : "領現金", costModel.describe());

        StrategyResult strategy;
        HoldingResult benchmark;
        try {
            strategy = new StrategySimulator(costModel).simulate(data.stock(), data.prices(), data.dividends(),
                    data.splits(), entry, exit, request.initialCapital(), period.start(), reinvest);
            // 對照組：同樣的交易成本、同樣的股利處理方式，才比較得公平
            benchmark = new BuyAndHoldSimulator(costModel).simulate(data.stock(), periodPrices, data.dividends(),
                    data.splits(), request.initialCapital(), reinvest);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        log.info("[條件回測] 完成：{} 條件 {}% vs 一直持有 {}%，交易 {} 筆，耗時 {} ms",
                stockId, strategy.metrics().totalReturnPercent(), benchmark.metrics().totalReturnPercent(),
                strategy.tradeCount(), System.currentTimeMillis() - started);

        return new StrategyResponse(stockId, data.stock().getName(), period.start(), period.end(),
                request.initialCapital(), reinvest, entry.describe(), exit.describe(), costModel.describe(),
                strategy, benchmark, assumptions(costModel, reinvest, strategy, benchmark));
    }

    private List<ConditionRule> toRules(List<ConditionSpec> specs) {
        return specs.stream().map(spec -> new ConditionRule(spec.type(), spec.value())).toList();
    }

    private List<String> assumptions(CostModel costModel, boolean reinvest,
                                     StrategyResult strategy, HoldingResult benchmark) {
        List<String> list = new ArrayList<>();
        list.add("每天收盤後判斷條件，成立的話隔天以開盤價成交（收盤時才知道條件成立，不能用當天價格成交）");
        list.add("空手時只看買進條件，持有時只看賣出條件；同一時間最多持有一筆，每次買進用全部現金");
        list.add("均線、近期高低點會往起始日之前多抓資料來計算（例如年線要 240 個交易日），回測一開始就能判斷條件；"
                + "如果資料庫沒有那麼早的價格，一開始的幾天不會產生訊號");
        list.add(reinvest
                ? "持有期間領到的股利，在除息日以收盤價再投入；空手時遇到除息領不到股利"
                : "持有期間領到的股利存成現金，下次買進時一起使用；空手時遇到除息領不到股利");
        list.add("持有期間遇到股票分割，股數與買進價依比例調整");
        list.add("交易成本：" + costModel.describe());
        list.add("對照組「一直持有」：起始日開盤買進持有到最後，股利" + (reinvest ? "同樣再投入" : "同樣存成現金"));
        list.add("期末仍持有的部位以最後收盤價估值，未扣除賣出成本");
        if (strategy.pendingSignal() != null) {
            list.add(strategy.pendingSignal());
        }
        list.addAll(benchmark.warnings());
        return list;
    }
}
