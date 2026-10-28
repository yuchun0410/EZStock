package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.DividendRecord;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.service.DividendQueryService;
import com.ezstock.service.PriceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 股利紀錄與殖利率。
 * 跟 StockQueryServiceImpl 一樣不加唯讀交易，因為可能觸發按需載入。
 */
@Service
public class DividendQueryServiceImpl implements DividendQueryService {

    private static final Logger log = LoggerFactory.getLogger(DividendQueryServiceImpl.class);

    static final int DIVIDEND_YEARS = 10;

    /** 往前找除息前收盤時，最多回頭幾天（涵蓋春節之類的長假） */
    static final int LOOKBACK_DAYS = 20;

    private final StockDao stockDao;
    private final DividendDao dividendDao;
    private final DailyPriceDao dailyPriceDao;
    private final PriceLoader priceLoader;
    private final Clock clock;

    public DividendQueryServiceImpl(StockDao stockDao, DividendDao dividendDao, DailyPriceDao dailyPriceDao,
                                    PriceLoader priceLoader, Clock clock) {
        this.stockDao = stockDao;
        this.dividendDao = dividendDao;
        this.dailyPriceDao = dailyPriceDao;
        this.priceLoader = priceLoader;
        this.clock = clock;
    }

    @Override
    public List<DividendRecord> getDividends(String stockId) {
        if (stockDao.findById(stockId).isEmpty()) {
            throw new ResourceNotFoundException("查無股票 " + stockId);
        }
        priceLoader.ensureLoaded(stockId);

        LocalDate today = LocalDate.now(clock);
        List<Dividend> dividends = dividendDao.findByStockAndDateRange(stockId, today.minusYears(DIVIDEND_YEARS), today);
        if (dividends.isEmpty()) {
            log.info("查詢股利 {}：沒有除息紀錄", stockId);
            return List.of();
        }

        // 避免 N+1 查詢：不要每筆股利各查一次價格，而是一次把整段期間的收盤價撈出來，
        // 放進 TreeMap（依日期排序），再用 lowerEntry 找「除息日之前最近的一個交易日」。
        LocalDate firstExDate = dividends.getFirst().getExDate();
        LocalDate lastExDate = dividends.getLast().getExDate();
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (DailyPrice price : dailyPriceDao.findByStockAndDateRange(
                stockId, firstExDate.minusDays(LOOKBACK_DAYS), lastExDate)) {
            closes.put(price.getTradeDate(), price.getClosePrice());
        }

        List<DividendRecord> result = dividends.stream()
                .map(dividend -> toRecord(dividend, closes))
                .sorted(Comparator.comparing(DividendRecord::exDate).reversed())
                .toList();
        log.info("查詢股利 {}：{} 筆，期間 {} ~ {}", stockId, result.size(), firstExDate, lastExDate);
        return result;
    }

    private DividendRecord toRecord(Dividend dividend, TreeMap<LocalDate, BigDecimal> closes) {
        LocalDate exDate = dividend.getExDate();
        Map.Entry<LocalDate, BigDecimal> before = closes.lowerEntry(exDate);
        if (before == null || before.getKey().isBefore(exDate.minusDays(LOOKBACK_DAYS))) {
            log.debug("股利 {} 除息日 {}：找不到除息前的收盤價，殖利率不計算",
                    dividend.getStock().getStockId(), exDate);
            return new DividendRecord(exDate, dividend.getCashDividend(), null, null);
        }
        BigDecimal close = before.getValue();
        BigDecimal yield = dividend.getCashDividend()
                .multiply(BigDecimal.valueOf(100))
                .divide(close, 2, RoundingMode.HALF_UP);
        return new DividendRecord(exDate, dividend.getCashDividend(), close, yield);
    }
}
