package com.ezstock.service.impl;

import com.ezstock.config.ImportProperties;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.datasource.CashDividend;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.StockInfo;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.service.ImportResult;
import com.ezstock.service.ImportService;
import com.ezstock.service.StockImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);

    private final MarketDataSource marketDataSource;
    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final DividendDao dividendDao;
    private final ImportProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 有兩個建構子時，要用 @Autowired 告訴 Spring 用哪一個 */
    @Autowired
    public ImportServiceImpl(MarketDataSource marketDataSource,
                             StockDao stockDao,
                             DailyPriceDao dailyPriceDao,
                             DividendDao dividendDao,
                             ImportProperties properties,
                             PlatformTransactionManager transactionManager) {
        this(marketDataSource, stockDao, dailyPriceDao, dividendDao, properties,
                transactionManager, Clock.systemDefaultZone());
    }

    /** 測試用：可以指定「今天」是哪一天 */
    ImportServiceImpl(MarketDataSource marketDataSource,
                      StockDao stockDao,
                      DailyPriceDao dailyPriceDao,
                      DividendDao dividendDao,
                      ImportProperties properties,
                      PlatformTransactionManager transactionManager,
                      Clock clock) {
        this.marketDataSource = marketDataSource;
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.dividendDao = dividendDao;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public ImportResult importAll() {
        long start = System.currentTimeMillis();
        List<String> stockIds = properties.stockIds();
        log.info("匯入開始：股票清單，以及 {} 檔股票最近 {} 年的日線與股利 {}",
                stockIds.size(), properties.years(), stockIds);

        // 1. 股票清單：失敗就沒辦法繼續（價格要掛在股票底下），直接往外丟
        int stockListCount = importStockList();

        // 2. 每一檔各自處理：某一檔失敗只記錄下來，不影響其他檔
        List<StockImportResult> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < stockIds.size(); i++) {
            String stockId = stockIds.get(i);
            log.info("匯入進度 {}/{}：{}", i + 1, stockIds.size(), stockId);
            try {
                succeeded.add(importStock(stockId));
            } catch (Exception e) {
                failed.add(stockId);
                log.error("匯入 {} 失敗，略過這檔繼續下一檔：{}", stockId, e.getMessage(), e);
            }
        }

        ImportResult result = new ImportResult(stockListCount, succeeded, failed,
                System.currentTimeMillis() - start);
        log.info("匯入完成：股票清單 {} 檔；價格成功 {} 檔、失敗 {} 檔 {}；新增日線 {} 筆、股利 {} 筆；耗時 {} 秒",
                result.stockListCount(), succeeded.size(), failed.size(), failed,
                result.totalPricesInserted(), result.totalDividendsInserted(),
                result.elapsedMillis() / 1000);
        return result;
    }

    @Override
    public StockImportResult importStock(String stockId) {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusYears(properties.years());
        log.debug("匯入 {} 開始，期間 {} ~ {}", stockId, from, to);

        // 先在交易外呼叫外部 API：網路可能很慢，不要讓資料庫交易一直開著等
        List<PriceBar> bars = marketDataSource.fetchPrices(stockId, from, to);
        List<CashDividend> cashDividends = marketDataSource.fetchCashDividends(stockId, from, to);

        // 再用一個交易寫入：日線和股利要嘛都成功，要嘛都不寫
        StockImportResult result = transactionTemplate.execute(status -> {
            Stock stock = stockDao.findById(stockId)
                    .orElseThrow(() -> new IllegalStateException("股票清單中找不到 " + stockId + "，請先匯入股票清單"));
            return saveNewData(stock, bars, cashDividends, from, to);
        });

        log.info("匯入 {} 完成：日線新增 {} 筆、略過 {} 筆（已存在）；股利新增 {} 筆、略過 {} 筆（已存在）",
                stockId, result.pricesInserted(), result.pricesSkipped(),
                result.dividendsInserted(), result.dividendsSkipped());
        return result;
    }

    private int importStockList() {
        List<StockInfo> infos = marketDataSource.fetchStockInfos();
        log.info("儲存股票清單 開始，共 {} 檔", infos.size());
        transactionTemplate.executeWithoutResult(status -> {
            for (StockInfo info : infos) {
                stockDao.save(new Stock(info.stockId(), info.name(), info.type(), info.industry()));
            }
        });
        log.info("儲存股票清單 完成，共 {} 檔", infos.size());
        return infos.size();
    }

    /** 只新增資料庫裡還沒有的日期，重複執行匯入也不會重複寫入 */
    private StockImportResult saveNewData(Stock stock, List<PriceBar> bars, List<CashDividend> cashDividends,
                                          LocalDate from, LocalDate to) {
        String stockId = stock.getStockId();

        Set<LocalDate> existingDates = new HashSet<>(dailyPriceDao.findTradeDates(stockId, from, to));
        List<DailyPrice> newPrices = new ArrayList<>();
        for (PriceBar bar : bars) {
            if (!existingDates.contains(bar.date())) {
                newPrices.add(new DailyPrice(stock, bar.date(),
                        bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()));
            }
        }
        int pricesInserted = dailyPriceDao.insertAll(newPrices);

        Set<LocalDate> existingExDates = new HashSet<>();
        for (Dividend d : dividendDao.findByStockAndDateRange(stockId, from, to)) {
            existingExDates.add(d.getExDate());
        }
        List<Dividend> newDividends = new ArrayList<>();
        for (CashDividend cash : cashDividends) {
            if (!existingExDates.contains(cash.exDate())) {
                newDividends.add(new Dividend(stock, cash.exDate(), cash.cashDividend()));
            }
        }
        int dividendsInserted = dividendDao.insertAll(newDividends);

        return new StockImportResult(stockId,
                pricesInserted, bars.size() - newPrices.size(),
                dividendsInserted, cashDividends.size() - newDividends.size());
    }
}
