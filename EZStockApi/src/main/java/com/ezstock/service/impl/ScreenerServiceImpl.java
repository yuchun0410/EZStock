package com.ezstock.service.impl;

import com.ezstock.backtest.strategy.BarSeries;
import com.ezstock.backtest.strategy.ConditionContext;
import com.ezstock.backtest.strategy.ConditionFactory;
import com.ezstock.backtest.strategy.ConditionRule;
import com.ezstock.backtest.strategy.condition.Condition;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.ScreenMatch;
import com.ezstock.dto.ScreenRequest;
import com.ezstock.dto.ScreenResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Stock;
import com.ezstock.service.PriceLoader;
import com.ezstock.service.ScreenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 條件選股：對每一檔股票，拿「最新一天」當作今天，用跟條件回測一樣的 Condition 判斷。
 * 條件、指標的程式完全共用，所以選股和回測的結果一定對得起來。
 */
@Service
public class ScreenerServiceImpl implements ScreenerService {

    private static final Logger log = LoggerFactory.getLogger(ScreenerServiceImpl.class);

    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final PriceLoader priceLoader;
    private final Clock clock;

    public ScreenerServiceImpl(StockDao stockDao, DailyPriceDao dailyPriceDao, PriceLoader priceLoader, Clock clock) {
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.priceLoader = priceLoader;
        this.clock = clock;
    }

    @Override
    public ScreenResponse screen(ScreenRequest request) {
        long started = System.currentTimeMillis();
        List<ConditionRule> rules = request.conditions().stream()
                .map(spec -> new ConditionRule(spec.type(), spec.value()))
                .toList();
        Condition condition;
        try {
            condition = ConditionFactory.entry(rules);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        // 掃描全部時不逐檔補抓（交給每日排程），避免一次打幾百次外部 API；指定股票（例如自選股）才補抓
        boolean refresh = request.stockIds() != null && !request.stockIds().isEmpty();
        List<String> stockIds = universe(request.stockIds());
        int lookback = ConditionFactory.lookbackDays(rules);
        LocalDate today = LocalDate.now(clock);
        // 判斷「今天」要有 lookback 天的歷史，再加上今天自己和昨天（算漲跌）
        LocalDate from = today.minusDays(ConditionFactory.warmupCalendarDays(lookback + 2) + 7);
        log.info("[條件選股] 開始：{}，掃描 {} 檔（{}）", condition.describe(), stockIds.size(), refresh ? "指定股票" : "資料庫全部");

        List<ScreenMatch> matches = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int scanned = 0;
        for (String stockId : stockIds) {
            Optional<Stock> stock = stockDao.findById(stockId);
            if (stock.isEmpty()) {
                skipped.add(stockId + "：查無股票");
                continue;
            }
            if (refresh) {
                try {
                    priceLoader.ensureLoaded(stockId);
                } catch (Exception e) {
                    log.warn("[條件選股] {} 載入價格失敗，略過：{}", stockId, e.getMessage());
                    skipped.add(stockId + " " + stock.get().getName() + "：價格資料暫時無法取得");
                    continue;
                }
            }

            List<DailyPrice> prices = dailyPriceDao.findByStockAndDateRange(stockId, from, today);
            if (prices.size() < lookback + 1) {
                skipped.add(stockId + " " + stock.get().getName() + "：歷史資料不足 " + (lookback + 1) + " 天");
                continue;
            }
            scanned++;
            BarSeries series = new BarSeries(prices);
            int last = series.size() - 1;
            if (condition.isSatisfied(new ConditionContext(series, last, null))) {
                matches.add(toMatch(stock.get(), series, last));
                log.debug("[條件選股] {} {} 符合：{} 收盤 {}", stockId, stock.get().getName(),
                        series.date(last), series.close(last));
            }
        }

        log.info("[條件選股] 完成：掃描 {} 檔，符合 {} 檔，略過 {} 檔，耗時 {} ms",
                scanned, matches.size(), skipped.size(), System.currentTimeMillis() - started);
        return new ScreenResponse(condition.describe(), scanned, matches, skipped);
    }

    /** 有指定股票就用指定的（去掉重複、保留順序），沒有就是資料庫裡所有有價格的股票 */
    private List<String> universe(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return dailyPriceDao.findStockIdsWithPrices();
        }
        return new ArrayList<>(new LinkedHashSet<>(requested.stream().map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    private ScreenMatch toMatch(Stock stock, BarSeries series, int last) {
        BigDecimal close = series.close(last);
        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (last > 0) {
            BigDecimal previous = series.close(last - 1);
            change = close.subtract(previous);
            changePercent = change.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
        }
        return new ScreenMatch(stock.getStockId(), stock.getName(), stock.getType(), stock.getIndustry(),
                series.date(last), close, change, changePercent);
    }
}
