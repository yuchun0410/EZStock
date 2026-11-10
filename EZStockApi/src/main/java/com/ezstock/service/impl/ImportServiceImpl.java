package com.ezstock.service.impl;

import com.ezstock.config.ImportProperties;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockSplitDao;
import com.ezstock.datasource.CashDividend;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.ShareSplit;
import com.ezstock.datasource.StockInfo;
import com.ezstock.datasource.finmind.FinMindApiException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);

    private final MarketDataSource marketDataSource;
    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final DividendDao dividendDao;
    private final StockSplitDao stockSplitDao;
    private final ImportProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 有兩個建構子時，要用 @Autowired 告訴 Spring 用哪一個 */
    @Autowired
    public ImportServiceImpl(MarketDataSource marketDataSource,
                             StockDao stockDao,
                             DailyPriceDao dailyPriceDao,
                             DividendDao dividendDao,
                             StockSplitDao stockSplitDao,
                             ImportProperties properties,
                             PlatformTransactionManager transactionManager) {
        this(marketDataSource, stockDao, dailyPriceDao, dividendDao, stockSplitDao, properties,
                transactionManager, Clock.systemDefaultZone());
    }

    /** 測試用：可以指定「今天」是哪一天 */
    ImportServiceImpl(MarketDataSource marketDataSource,
                      StockDao stockDao,
                      DailyPriceDao dailyPriceDao,
                      DividendDao dividendDao,
                      StockSplitDao stockSplitDao,
                      ImportProperties properties,
                      PlatformTransactionManager transactionManager,
                      Clock clock) {
        this.marketDataSource = marketDataSource;
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.dividendDao = dividendDao;
        this.stockSplitDao = stockSplitDao;
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

        // 1. 股票清單：台股失敗就沒辦法繼續（價格要掛在股票底下），直接往外丟；
        //    美股清單失敗只記錄，不影響台股
        int stockListCount = importStockList(marketDataSource.fetchStockInfos(), "台股");
        try {
            stockListCount += importStockList(marketDataSource.fetchUsStockInfos(), "美股");
        } catch (Exception e) {
            log.error("美股清單匯入失敗，這次先略過：{}", e.getMessage(), e);
        }

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
        log.info("匯入完成：股票清單 {} 檔；價格成功 {} 檔、失敗 {} 檔 {}；新增日線 {} 筆、股利 {} 筆、股票分割 {} 筆；耗時 {} 秒",
                result.stockListCount(), succeeded.size(), failed.size(), failed,
                result.totalPricesInserted(), result.totalDividendsInserted(), result.totalSplitsInserted(),
                result.elapsedMillis() / 1000);
        return result;
    }

    @Override
    public int importUsStockList() {
        long start = System.currentTimeMillis();
        int count = importStockList(marketDataSource.fetchUsStockInfos(), "美股");
        log.info("美股清單匯入完成：{} 檔，耗時 {} 秒", count, (System.currentTimeMillis() - start) / 1000);
        return count;
    }

    @Override
    public StockImportResult importStock(String stockId) {
        return importStock(findStock(stockId));
    }

    private StockImportResult importStock(Stock stock) {
        LocalDate to = LocalDate.now(clock);
        return importRange(stock, to.minusYears(properties.years()), to);
    }

    private Stock findStock(String stockId) {
        return stockDao.findById(stockId)
                .orElseThrow(() -> new IllegalStateException("股票清單中找不到 " + stockId + "，請先匯入股票清單"));
    }

    @Override
    public StockImportResult updateStock(String stockId) {
        LocalDate today = LocalDate.now(clock);
        Stock stock = findStock(stockId);
        Optional<DailyPrice> latest = dailyPriceDao.findLatest(stockId);
        if (latest.isEmpty()) {
            log.info("更新 {}：資料庫沒有價格，改為完整匯入", stockId);
            return importStock(stock);
        }
        if (stock.isUs()) {
            return updateUsStock(stock, latest.get(), today);
        }
        LocalDate from = latest.get().getTradeDate().plusDays(1);
        if (from.isAfter(today)) {
            log.debug("更新 {}：已經是最新（{}），不呼叫外部資料來源", stockId, latest.get().getTradeDate());
            return new StockImportResult(stockId, 0, 0, 0, 0, 0);
        }
        log.info("更新 {}：資料庫最新是 {}，補抓 {} ~ {}", stockId, latest.get().getTradeDate(), from, today);
        return importRange(stock, from, today);
    }

    /**
     * 美股存的是還原價：之後只要有除息或分割，過去每一天的還原價都會跟著變。
     * 所以補資料時多抓「資料庫最新那一天」來比對：
     * 那天的收盤跟資料庫一樣，代表還原比例沒變，只要補新的日子；
     * 不一樣，代表中間有除息或分割，舊資料全部作廢，整段重新匯入，不然新舊資料接起來會有一個假的跳空。
     */
    private StockImportResult updateUsStock(Stock stock, DailyPrice latest, LocalDate today) {
        String stockId = stock.getStockId();
        LocalDate latestDate = latest.getTradeDate();
        if (!latestDate.isBefore(today)) {
            log.debug("更新美股 {}：已經是最新（{}），不呼叫外部資料來源", stockId, latestDate);
            return new StockImportResult(stockId, 0, 0, 0, 0, 0);
        }

        List<PriceBar> bars = marketDataSource.fetchUsPrices(stockId, latestDate, today);
        Optional<PriceBar> overlap = bars.stream().filter(bar -> bar.date().equals(latestDate)).findFirst();
        if (overlap.isPresent() && overlap.get().close().compareTo(latest.getClosePrice()) != 0) {
            log.info("更新美股 {}：{} 的還原收盤從 {} 變成 {}，代表期間有除息或分割，整段重新匯入",
                    stockId, latestDate, latest.getClosePrice(), overlap.get().close());
            return rebuildUsStock(stock);
        }

        log.info("更新美股 {}：資料庫最新是 {}，還原比例沒變，補抓到 {}", stockId, latestDate, today);
        StockImportResult result = transactionTemplate.execute(status ->
                saveNewData(stock, bars, List.of(), List.of(), latestDate, today));
        log.info("更新美股 {} 完成：日線新增 {} 筆", stockId, result.pricesInserted());
        return result;
    }

    /** 刪掉這檔美股的全部日線，重新抓完整期間。先抓再刪，外部 API 失敗時舊資料還在 */
    private StockImportResult rebuildUsStock(Stock stock) {
        String stockId = stock.getStockId();
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusYears(properties.years());
        List<PriceBar> bars = marketDataSource.fetchUsPrices(stockId, from, to);
        if (bars.isEmpty()) {
            log.warn("重新匯入美股 {}：外部資料來源沒有回傳資料，保留舊資料", stockId);
            return new StockImportResult(stockId, 0, 0, 0, 0, 0);
        }
        StockImportResult result = transactionTemplate.execute(status -> {
            int deleted = dailyPriceDao.deleteByStock(stockId);
            log.info("重新匯入美股 {}：刪除舊日線 {} 筆", stockId, deleted);
            return saveNewData(stock, bars, List.of(), List.of(), from, to);
        });
        log.info("重新匯入美股 {} 完成：日線 {} 筆（{} ~ {}）", stockId, result.pricesInserted(), from, to);
        return result;
    }

    @Override
    public ImportResult updateAll() {
        long start = System.currentTimeMillis();
        List<String> stockIds = dailyPriceDao.findStockIdsWithPrices();
        log.info("每日更新開始：共 {} 檔 {}", stockIds.size(), stockIds);

        List<StockImportResult> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String stockId : stockIds) {
            try {
                succeeded.add(updateStock(stockId));
            } catch (Exception e) {
                failed.add(stockId);
                log.error("每日更新 {} 失敗，略過這檔繼續下一檔：{}", stockId, e.getMessage(), e);
            }
        }

        ImportResult result = new ImportResult(0, succeeded, failed, System.currentTimeMillis() - start);
        log.info("每日更新完成：成功 {} 檔、失敗 {} 檔 {}；新增日線 {} 筆、股利 {} 筆、股票分割 {} 筆；耗時 {} 秒",
                succeeded.size(), failed.size(), failed, result.totalPricesInserted(),
                result.totalDividendsInserted(), result.totalSplitsInserted(), result.elapsedMillis() / 1000);
        return result;
    }

    /**
     * 匯入某段期間的日線、股利、分割，只新增資料庫還沒有的。
     * 美股只有還原價，沒有股利、分割資料（還原價已經包含了）。
     */
    private StockImportResult importRange(Stock stock, LocalDate from, LocalDate to) {
        String stockId = stock.getStockId();
        log.debug("匯入 {}（{}）開始，期間 {} ~ {}", stockId, stock.getMarket(), from, to);

        // 先在交易外呼叫外部 API：網路可能很慢，不要讓資料庫交易一直開著等
        List<PriceBar> bars;
        List<CashDividend> cashDividends;
        List<ShareSplit> shareSplits;
        if (stock.isUs()) {
            bars = marketDataSource.fetchUsPrices(stockId, from, to);
            cashDividends = List.of();
            shareSplits = List.of();
        } else {
            bars = marketDataSource.fetchPrices(stockId, from, to);
            cashDividends = marketDataSource.fetchCashDividends(stockId, from, to);
            shareSplits = fetchShareSplitsSafely(stockId, from, to);
        }

        // 再用一個交易寫入：日線、股利、股票分割要嘛都成功，要嘛都不寫
        StockImportResult result = transactionTemplate.execute(status ->
                saveNewData(stock, bars, cashDividends, shareSplits, from, to));

        log.info("匯入 {} 完成：日線新增 {} 筆、略過 {} 筆（已存在）；股利新增 {} 筆、略過 {} 筆（已存在）；股票分割新增 {} 筆",
                stockId, result.pricesInserted(), result.pricesSkipped(),
                result.dividendsInserted(), result.dividendsSkipped(), result.splitsInserted());
        return result;
    }

    /**
     * 股票分割抓不到時不讓整檔匯入失敗：價格和股利才是主要資料，
     * 沒有分割資料只會影響少數有分割的股票，回測那邊也有異常跳動的警告可以補救。
     * 不重試，避免一直打 FinMind 被封鎖。
     */
    private List<ShareSplit> fetchShareSplitsSafely(String stockId, LocalDate from, LocalDate to) {
        try {
            return marketDataSource.fetchShareSplits(stockId, from, to);
        } catch (FinMindApiException e) {
            log.warn("匯入 {}：股票分割資料抓取失敗，這次先略過：{}", stockId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 儲存股票清單，只寫新的或有變動的。
     * 美股有好幾千檔，每次匯入都全部寫一次太慢；大部分股票的名稱、產業不會變，比對後跳過。
     */
    private int importStockList(List<StockInfo> infos, String label) {
        Map<String, Stock> existing = stockDao.findAll().stream()
                .collect(Collectors.toMap(Stock::getStockId, Function.identity()));
        List<Stock> changed = new ArrayList<>();
        for (StockInfo info : infos) {
            Stock current = existing.get(info.stockId());
            if (current == null || !sameInfo(current, info)) {
                changed.add(new Stock(info.stockId(), info.name(), info.type(), info.market(), info.industry()));
            }
        }
        log.info("儲存{}清單 開始：共 {} 檔，其中新增或變動 {} 檔", label, infos.size(), changed.size());
        transactionTemplate.executeWithoutResult(status -> changed.forEach(stockDao::save));
        log.info("儲存{}清單 完成", label);
        return infos.size();
    }

    private static boolean sameInfo(Stock stock, StockInfo info) {
        return Objects.equals(stock.getName(), info.name())
                && stock.getType() == info.type()
                && stock.getMarket() == info.market()
                && Objects.equals(stock.getIndustry(), info.industry());
    }

    /** 只新增資料庫裡還沒有的日期，重複執行匯入也不會重複寫入 */
    private StockImportResult saveNewData(Stock stock, List<PriceBar> bars, List<CashDividend> cashDividends,
                                          List<ShareSplit> shareSplits, LocalDate from, LocalDate to) {
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

        Set<LocalDate> existingSplitDates = new HashSet<>();
        for (StockSplit s : stockSplitDao.findByStockAndDateRange(stockId, from, to)) {
            existingSplitDates.add(s.getSplitDate());
        }
        List<StockSplit> newSplits = new ArrayList<>();
        for (ShareSplit split : shareSplits) {
            if (existingSplitDates.add(split.date())) {
                newSplits.add(new StockSplit(stock, split.date(), split.type(), split.beforePrice(), split.afterPrice()));
            }
        }
        int splitsInserted = stockSplitDao.insertAll(newSplits);

        return new StockImportResult(stockId,
                pricesInserted, bars.size() - newPrices.size(),
                dividendsInserted, cashDividends.size() - newDividends.size(),
                splitsInserted);
    }
}
