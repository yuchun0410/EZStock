package com.ezstock.service.impl;

import com.ezstock.backtest.BuyAndHoldSimulator;
import com.ezstock.backtest.HoldingResult;
import com.ezstock.backtest.cost.CostModel;
import com.ezstock.backtest.cost.TaiwanStockCostModel;
import com.ezstock.backtest.cost.ZeroCostModel;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockSplitDao;
import com.ezstock.dto.CompareRequest;
import com.ezstock.dto.CompareResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
import com.ezstock.service.BacktestService;
import com.ezstock.service.PriceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 負責「準備資料」：查股票、確保價格已載入、撈價格與股利，再交給 {@link BuyAndHoldSimulator} 計算。
 * 計算規則都在模擬器裡，這裡不做任何數學。
 * 一樣不加唯讀交易，因為可能觸發按需載入。
 */
@Service
public class BacktestServiceImpl implements BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestServiceImpl.class);

    static final int MAX_YEARS = 10;

    /** 起始日之後超過這麼多天才有第一筆價格，代表那檔當時可能還沒上市，要提醒使用者 */
    static final int LATE_START_WARNING_DAYS = 14;

    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final DividendDao dividendDao;
    private final StockSplitDao stockSplitDao;
    private final PriceLoader priceLoader;
    private final Clock clock;

    public BacktestServiceImpl(StockDao stockDao, DailyPriceDao dailyPriceDao, DividendDao dividendDao,
                               StockSplitDao stockSplitDao, PriceLoader priceLoader, Clock clock) {
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.dividendDao = dividendDao;
        this.stockSplitDao = stockSplitDao;
        this.priceLoader = priceLoader;
        this.clock = clock;
    }

    @Override
    public CompareResponse compare(CompareRequest request) {
        long started = System.currentTimeMillis();
        LocalDate today = LocalDate.now(clock);
        LocalDate start = request.startDate();
        LocalDate end = request.endDate() != null ? request.endDate() : today;
        boolean reinvest = request.reinvestDividends() == null || request.reinvestDividends();
        // 去掉重複的代號，但保留使用者選的順序
        List<String> stockIds = new ArrayList<>(new LinkedHashSet<>(
                request.stockIds().stream().map(String::trim).toList()));

        if (!start.isBefore(end)) {
            throw new BadRequestException("起始日 " + start + " 必須早於結束日 " + end);
        }
        if (end.isAfter(today)) {
            throw new BadRequestException("結束日不能是未來");
        }
        if (start.isBefore(end.minusYears(MAX_YEARS))) {
            throw new BadRequestException("回測期間最多 " + MAX_YEARS + " 年");
        }

        CostModel costModel = costModel(request);
        BuyAndHoldSimulator simulator = new BuyAndHoldSimulator(costModel);
        log.info("[標的比較] 開始：{}，期間 {} ~ {}，每檔投入 {} 元，股利{}，{}",
                stockIds, start, end, request.initialCapital().toPlainString(),
                reinvest ? "再投入" : "領現金", costModel.describe());

        List<HoldingResult> results = new ArrayList<>();
        List<String> assumptions = new ArrayList<>(baseAssumptions(reinvest, costModel));
        for (String stockId : stockIds) {
            Stock stock = stockDao.findById(stockId)
                    .orElseThrow(() -> new ResourceNotFoundException("查無股票 " + stockId));
            priceLoader.ensureLoaded(stockId);

            List<DailyPrice> prices = dailyPriceDao.findByStockAndDateRange(stockId, start, end);
            if (prices.isEmpty()) {
                throw new BadRequestException(stockId + " " + stock.getName() + " 在 " + start + " ~ " + end + " 沒有價格資料");
            }
            List<Dividend> dividends = dividendDao.findByStockAndDateRange(stockId, start, end);
            List<StockSplit> splits = stockSplitDao.findByStockAndDateRange(stockId, start, end);

            LocalDate firstTrade = prices.getFirst().getTradeDate();
            if (ChronoUnit.DAYS.between(start, firstTrade) > LATE_START_WARNING_DAYS) {
                String note = stockId + " " + stock.getName() + " 最早的價格是 " + firstTrade
                        + "，可能當時還沒上市或資料庫沒有更早的資料，所以從這天才買進";
                log.warn("[標的比較] {}", note);
                assumptions.add(note);
            }

            HoldingResult result;
            try {
                result = simulator.simulate(stock, prices, dividends, splits, request.initialCapital(), reinvest);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
            results.add(result);
            assumptions.addAll(result.warnings());
        }

        log.info("[標的比較] 完成：{} 檔，耗時 {} ms", results.size(), System.currentTimeMillis() - started);
        return new CompareResponse(start, end, request.initialCapital(), reinvest, costModel.describe(),
                results, assumptions);
    }

    private CostModel costModel(CompareRequest request) {
        if (Boolean.FALSE.equals(request.includeCosts())) {
            return new ZeroCostModel();
        }
        BigDecimal discount = request.feeDiscount() != null ? request.feeDiscount() : BigDecimal.ONE;
        return new TaiwanStockCostModel(discount);
    }

    private List<String> baseAssumptions(boolean reinvest, CostModel costModel) {
        List<String> list = new ArrayList<>();
        list.add("起始日之後的第一個交易日，用開盤價買進，可買零股");
        list.add(reinvest ? "股利在除息日當天以收盤價再投入" : "股利在除息日當天入帳，以現金保留");
        list.add("交易成本：" + costModel.describe());
        list.add("股票分割、面額變更的那天，股數依比例調整（例如 1 拆 4 股數變 4 倍），價值不變");
        list.add("期末資產以最後一個交易日的收盤價計算，未扣除賣出成本");
        list.add("未計股利所得稅與二代健保補充保費；股利實際約在除息後一個月才發放");
        return list;
    }
}
