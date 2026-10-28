package com.ezstock.service.impl;

import com.ezstock.backtest.cost.CostModel;
import com.ezstock.backtest.cost.TaiwanStockCostModel;
import com.ezstock.backtest.cost.ZeroCostModel;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockSplitDao;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
import com.ezstock.service.PriceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 各種回測共用的「準備資料」：檢查期間、查股票、確保價格已載入、撈價格與股利與分割。
 * 標的比較和條件回測都用它，規則只寫一次。
 */
@Component
public class BacktestDataLoader {

    private static final Logger log = LoggerFactory.getLogger(BacktestDataLoader.class);

    static final int MAX_YEARS = 10;

    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final DividendDao dividendDao;
    private final StockSplitDao stockSplitDao;
    private final PriceLoader priceLoader;
    private final Clock clock;

    public BacktestDataLoader(StockDao stockDao, DailyPriceDao dailyPriceDao, DividendDao dividendDao,
                              StockSplitDao stockSplitDao, PriceLoader priceLoader, Clock clock) {
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.dividendDao = dividendDao;
        this.stockSplitDao = stockSplitDao;
        this.priceLoader = priceLoader;
        this.clock = clock;
    }

    /** 回測期間：結束日沒給就是今天 */
    public record Period(LocalDate start, LocalDate end) {
    }

    /** 一檔股票在回測期間的所有資料 */
    public record StockData(Stock stock, List<DailyPrice> prices, List<Dividend> dividends, List<StockSplit> splits) {
    }

    /** 檢查期間是否合理：起始日早於結束日、結束日不是未來、最多 10 年 */
    public Period resolvePeriod(LocalDate start, LocalDate endOrNull) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = endOrNull != null ? endOrNull : today;
        if (!start.isBefore(end)) {
            throw new BadRequestException("起始日 " + start + " 必須早於結束日 " + end);
        }
        if (end.isAfter(today)) {
            throw new BadRequestException("結束日不能是未來");
        }
        if (start.isBefore(end.minusYears(MAX_YEARS))) {
            throw new BadRequestException("回測期間最多 " + MAX_YEARS + " 年");
        }
        return new Period(start, end);
    }

    /** 查股票（查無丟 404）→ 確保價格已載入 → 撈期間內的價格、股利、分割（沒有價格丟 400） */
    public StockData load(String stockId, Period period) {
        Stock stock = stockDao.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("查無股票 " + stockId));
        priceLoader.ensureLoaded(stockId);

        List<DailyPrice> prices = dailyPriceDao.findByStockAndDateRange(stockId, period.start(), period.end());
        if (prices.isEmpty()) {
            throw new BadRequestException(stockId + " " + stock.getName() + " 在 "
                    + period.start() + " ~ " + period.end() + " 沒有價格資料");
        }
        List<Dividend> dividends = dividendDao.findByStockAndDateRange(stockId, period.start(), period.end());
        List<StockSplit> splits = stockSplitDao.findByStockAndDateRange(stockId, period.start(), period.end());
        log.debug("[回測資料] {} 期間 {} ~ {}：日線 {} 筆、股利 {} 筆、分割 {} 筆",
                stockId, period.start(), period.end(), prices.size(), dividends.size(), splits.size());
        return new StockData(stock, prices, dividends, splits);
    }

    /** 依使用者的選擇決定交易成本模型 */
    public static CostModel costModel(Boolean includeCosts, BigDecimal feeDiscount) {
        if (Boolean.FALSE.equals(includeCosts)) {
            return new ZeroCostModel();
        }
        return new TaiwanStockCostModel(feeDiscount != null ? feeDiscount : BigDecimal.ONE);
    }
}
