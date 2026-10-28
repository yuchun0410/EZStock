package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.PricePoint;
import com.ezstock.dto.StockDetail;
import com.ezstock.dto.StockSummary;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Stock;
import com.ezstock.service.StockQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class StockQueryServiceImpl implements StockQueryService {

    private static final Logger log = LoggerFactory.getLogger(StockQueryServiceImpl.class);

    /** 搜尋最多回傳幾筆，避免輸入 "2" 就回傳上千檔 */
    static final int SEARCH_LIMIT = 20;

    /** 一次最多查幾年的日線，避免一次撈太多資料 */
    static final int MAX_PRICE_YEARS = 10;

    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final Clock clock;

    public StockQueryServiceImpl(StockDao stockDao, DailyPriceDao dailyPriceDao, Clock clock) {
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.clock = clock;
    }

    @Override
    public List<StockSummary> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BadRequestException("請輸入股票代號或名稱");
        }
        String trimmed = keyword.trim();
        List<StockSummary> result = stockDao.search(trimmed).stream()
                .limit(SEARCH_LIMIT)
                .map(StockSummary::from)
                .toList();
        log.info("搜尋股票 keyword={} 回傳 {} 筆", trimmed, result.size());
        return result;
    }

    @Override
    public StockDetail getDetail(String stockId) {
        Stock stock = findStock(stockId);

        Optional<DailyPrice> latest = dailyPriceDao.findLatest(stockId);
        if (latest.isEmpty()) {
            log.info("查詢個股 {} {}：尚無價格資料", stockId, stock.getName());
            return new StockDetail(stock.getStockId(), stock.getName(), stock.getType(), stock.getIndustry(),
                    null, null, null, null);
        }

        DailyPrice today = latest.get();
        // 找前一個交易日：往前 30 天內，最新一筆之前的那一筆（中間可能有連假）
        List<DailyPrice> recent = dailyPriceDao.findByStockAndDateRange(stockId,
                today.getTradeDate().minusDays(30), today.getTradeDate().minusDays(1));

        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (!recent.isEmpty()) {
            BigDecimal previousClose = recent.get(recent.size() - 1).getClosePrice();
            change = today.getClosePrice().subtract(previousClose);
            changePercent = change.multiply(BigDecimal.valueOf(100))
                    .divide(previousClose, 2, RoundingMode.HALF_UP);
        }

        log.info("查詢個股 {} {}：{} 收盤 {}，漲跌 {}（{}%）", stockId, stock.getName(),
                today.getTradeDate(), today.getClosePrice(), change, changePercent);
        return new StockDetail(stock.getStockId(), stock.getName(), stock.getType(), stock.getIndustry(),
                today.getTradeDate(), today.getClosePrice(), change, changePercent);
    }

    @Override
    public List<PricePoint> getPrices(String stockId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusYears(1);
        if (start.isAfter(end)) {
            throw new BadRequestException("開始日期 " + start + " 不能晚於結束日期 " + end);
        }
        if (start.isBefore(end.minusYears(MAX_PRICE_YEARS))) {
            throw new BadRequestException("查詢期間最多 " + MAX_PRICE_YEARS + " 年");
        }

        findStock(stockId);
        List<PricePoint> result = dailyPriceDao.findByStockAndDateRange(stockId, start, end).stream()
                .map(PricePoint::from)
                .toList();
        log.info("查詢日線 {} 期間 {} ~ {} 回傳 {} 筆", stockId, start, end, result.size());
        return result;
    }

    private Stock findStock(String stockId) {
        return stockDao.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("查無股票 " + stockId));
    }
}
