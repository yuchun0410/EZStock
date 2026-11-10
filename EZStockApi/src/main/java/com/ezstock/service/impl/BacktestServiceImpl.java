package com.ezstock.service.impl;

import com.ezstock.backtest.BuyAndHoldSimulator;
import com.ezstock.backtest.HoldingResult;
import com.ezstock.backtest.cost.CostModel;
import com.ezstock.dto.CompareRequest;
import com.ezstock.dto.CompareResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.Market;
import com.ezstock.model.Stock;
import com.ezstock.service.BacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用 {@link BacktestDataLoader} 準備資料，再交給 {@link BuyAndHoldSimulator} 計算。
 * 計算規則都在模擬器裡，這裡不做任何數學。
 * 一樣不加唯讀交易，因為可能觸發按需載入。
 */
@Service
public class BacktestServiceImpl implements BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestServiceImpl.class);

    /** 起始日之後超過這麼多天才有第一筆價格，代表那檔當時可能還沒上市，要提醒使用者 */
    static final int LATE_START_WARNING_DAYS = 14;

    private final BacktestDataLoader dataLoader;

    public BacktestServiceImpl(BacktestDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    @Override
    public CompareResponse compare(CompareRequest request) {
        long started = System.currentTimeMillis();
        BacktestDataLoader.Period period = dataLoader.resolvePeriod(request.startDate(), request.endDate());
        boolean reinvest = request.reinvestDividends() == null || request.reinvestDividends();
        // 去掉重複的代號，但保留使用者選的順序
        List<String> stockIds = new ArrayList<>(new LinkedHashSet<>(
                request.stockIds().stream().map(String::trim).toList()));

        // 先把每一檔的資料都準備好，才知道是哪個市場（台股、美股不能混在一起比）
        List<BacktestDataLoader.StockData> dataList = new ArrayList<>();
        for (String stockId : stockIds) {
            dataList.add(dataLoader.load(stockId, period));
        }
        Market market = singleMarket(dataList);
        CostModel costModel = BacktestDataLoader.costModel(market, request.includeCosts(), request.feeDiscount());
        BuyAndHoldSimulator simulator = new BuyAndHoldSimulator(costModel);
        log.info("[標的比較] 開始：{}（{}），期間 {} ~ {}，每檔投入 {} {}，股利{}，{}",
                stockIds, market, period.start(), period.end(), request.initialCapital().toPlainString(),
                market.currency(), reinvest ? "再投入" : "領現金", costModel.describe());

        List<HoldingResult> results = new ArrayList<>();
        List<String> assumptions = new ArrayList<>(baseAssumptions(market, reinvest, costModel));
        for (BacktestDataLoader.StockData data : dataList) {
            Stock stock = data.stock();

            LocalDate firstTrade = data.prices().getFirst().getTradeDate();
            if (ChronoUnit.DAYS.between(period.start(), firstTrade) > LATE_START_WARNING_DAYS) {
                String note = stock.getStockId() + " " + stock.getName() + " 最早的價格是 " + firstTrade
                        + "，可能當時還沒上市或資料庫沒有更早的資料，所以從這天才買進";
                log.warn("[標的比較] {}", note);
                assumptions.add(note);
            }

            HoldingResult result;
            try {
                result = simulator.simulate(stock, data.prices(), data.dividends(), data.splits(),
                        request.initialCapital(), reinvest);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
            results.add(result);
            assumptions.addAll(result.warnings());
        }

        log.info("[標的比較] 完成：{} 檔，耗時 {} ms", results.size(), System.currentTimeMillis() - started);
        return new CompareResponse(period.start(), period.end(), request.initialCapital(), market.currency(),
                reinvest, costModel.describe(), results, assumptions);
    }

    /**
     * 台股和美股不能放在一起比：一個是台幣、一個是美元，同樣「投入 10 萬」意義不同，
     * 要換算匯率才比較得公平（下一階段做）。
     */
    private Market singleMarket(List<BacktestDataLoader.StockData> dataList) {
        Set<Market> markets = dataList.stream().map(data -> data.stock().getMarket()).collect(Collectors.toSet());
        if (markets.size() > 1) {
            log.info("[標的比較] 拒絕：台股和美股混在一起 {}",
                    dataList.stream().map(data -> data.stock().getStockId()).toList());
            throw new BadRequestException("台股和美股的幣別不同，請分開比較");
        }
        return markets.iterator().next();
    }

    private List<String> baseAssumptions(Market market, boolean reinvest, CostModel costModel) {
        List<String> list = new ArrayList<>();
        list.add("起始日之後的第一個交易日，用開盤價買進，可買零股");
        if (market == Market.US) {
            list.add("金額單位為美元");
            list.add(BacktestDataLoader.US_ADJUSTED_PRICE_NOTE);
            list.add("交易成本：" + costModel.describe());
            list.add("期末資產以最後一個交易日的收盤價計算，未扣除賣出成本");
            return list;
        }
        list.add(reinvest ? "股利在除息日當天以收盤價再投入" : "股利在除息日當天入帳，以現金保留");
        list.add("交易成本：" + costModel.describe());
        list.add("股票分割、面額變更的那天，股數依比例調整（例如 1 拆 4 股數變 4 倍），價值不變");
        list.add("期末資產以最後一個交易日的收盤價計算，未扣除賣出成本");
        list.add("未計股利所得稅與二代健保補充保費；股利實際約在除息後一個月才發放");
        return list;
    }
}
