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
import com.ezstock.service.StrategyBacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        BacktestDataLoader.StockData data = dataLoader.load(stockId, period);
        CostModel costModel = BacktestDataLoader.costModel(request.includeCosts(), request.feeDiscount());
        log.info("[條件回測] 開始：{} {}，期間 {} ~ {}，本金 {} 元，買進「{}」，賣出「{}」，{}",
                stockId, data.stock().getName(), period.start(), period.end(),
                request.initialCapital().toPlainString(), entry.describe(), exit.describe(), costModel.describe());

        StrategyResult strategy;
        HoldingResult benchmark;
        try {
            strategy = new StrategySimulator(costModel).simulate(data.stock(), data.prices(), data.dividends(),
                    data.splits(), entry, exit, request.initialCapital());
            // 對照組：同樣的成本、股利領現金（條件回測的股利也是存成現金），才比較得公平
            benchmark = new BuyAndHoldSimulator(costModel).simulate(data.stock(), data.prices(), data.dividends(),
                    data.splits(), request.initialCapital(), false);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        log.info("[條件回測] 完成：{} 條件 {}% vs 一直持有 {}%，交易 {} 筆，耗時 {} ms",
                stockId, strategy.metrics().totalReturnPercent(), benchmark.metrics().totalReturnPercent(),
                strategy.tradeCount(), System.currentTimeMillis() - started);

        return new StrategyResponse(stockId, data.stock().getName(), period.start(), period.end(),
                request.initialCapital(), entry.describe(), exit.describe(), costModel.describe(),
                strategy, benchmark, assumptions(costModel, strategy, benchmark));
    }

    private List<ConditionRule> toRules(List<ConditionSpec> specs) {
        return specs.stream().map(spec -> new ConditionRule(spec.type(), spec.value())).toList();
    }

    private List<String> assumptions(CostModel costModel, StrategyResult strategy, HoldingResult benchmark) {
        List<String> list = new ArrayList<>();
        list.add("每天收盤後判斷條件，成立的話隔天以開盤價成交（收盤時才知道條件成立，不能用當天價格成交）");
        list.add("空手時只看買進條件，持有時只看賣出條件；同一時間最多持有一筆，每次買進用全部現金");
        list.add("均線、N 日高低點需要先累積幾天資料，期間一開始的幾天不會產生訊號");
        list.add("持有期間的股利存成現金，下次買進時一起使用；分割時股數與買進價依比例調整");
        list.add("交易成本：" + costModel.describe());
        list.add("對照組「一直持有」：第一天開盤買進持有到最後，股利同樣存成現金");
        list.add("期末仍持有的部位以最後收盤價估值，未扣除賣出成本");
        if (strategy.pendingSignal() != null) {
            list.add(strategy.pendingSignal());
        }
        list.addAll(benchmark.warnings());
        return list;
    }
}
