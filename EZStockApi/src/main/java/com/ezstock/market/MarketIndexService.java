package com.ezstock.market;

import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.PriceBar;
import com.ezstock.model.Market;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 市場概況的指數行情，每個指數在記憶體裡記 30 分鐘：
 * 指數是每日收盤資料，首頁每次打開都去問外部 API 太浪費（FinMind 每小時有呼叫上限）。
 * 一個指數抓失敗，先用舊的資料；完全沒有就回傳空的，其他指數照常顯示。
 */
@Service
public class MarketIndexService {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexService.class);

    static final Duration TTL = Duration.ofMinutes(30);
    static final int TREND_MONTHS = 3;

    private final MarketDataSource marketDataSource;
    private final Clock clock;

    private record Cached(IndexQuote quote, Instant fetchedAt) {
    }

    private final Map<MarketIndex, Cached> cache = new ConcurrentHashMap<>();

    /** 每個指數一把鎖（不直接鎖 enum 本身，enum 是全域共用的物件，別的程式也可能拿去鎖） */
    private final Map<MarketIndex, Object> locks = new EnumMap<>(MarketIndex.class);

    public MarketIndexService(MarketDataSource marketDataSource, Clock clock) {
        this.marketDataSource = marketDataSource;
        this.clock = clock;
        for (MarketIndex index : MarketIndex.values()) {
            locks.put(index, new Object());
        }
    }

    /** 全部指數，順序固定：加權指數、道瓊、S&P 500、那斯達克 */
    public List<IndexQuote> all() {
        List<IndexQuote> quotes = new ArrayList<>();
        for (MarketIndex index : MarketIndex.values()) {
            quotes.add(quote(index));
        }
        return quotes;
    }

    private IndexQuote quote(MarketIndex index) {
        Cached cached = cache.get(index);
        if (cached != null && Duration.between(cached.fetchedAt(), clock.instant()).compareTo(TTL) < 0) {
            return cached.quote();
        }
        // 同一個指數同時有很多人打開首頁，只讓一個請求去抓
        synchronized (locks.get(index)) {
            cached = cache.get(index);
            if (cached != null && Duration.between(cached.fetchedAt(), clock.instant()).compareTo(TTL) < 0) {
                return cached.quote();
            }
            try {
                IndexQuote quote = fetch(index);
                cache.put(index, new Cached(quote, clock.instant()));
                log.info("[市場概況] 更新 {} {}：{} 收盤 {}，漲跌 {}（{}%）", index.code(), index.displayName(),
                        quote.date(), quote.close(), quote.change(), quote.changePercent());
                return quote;
            } catch (Exception e) {
                log.warn("[市場概況] {} 抓取失敗：{}", index.code(), e.getMessage());
                if (cached != null) {
                    // 舊資料先用，但把時間往後延 5 分鐘再重試，避免每個請求都去打外部 API
                    cache.put(index, new Cached(cached.quote(), clock.instant().minus(TTL).plus(Duration.ofMinutes(5))));
                    return cached.quote();
                }
                return empty(index);
            }
        }
    }

    private IndexQuote fetch(MarketIndex index) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusMonths(TREND_MONTHS);
        List<PriceBar> bars = index.market() == Market.US
                ? marketDataSource.fetchUsPrices(index.code(), from, today)
                : marketDataSource.fetchPrices(index.code(), from, today);
        if (bars.isEmpty()) {
            throw new IllegalStateException("外部資料來源沒有回傳資料");
        }
        List<IndexQuote.Point> points = bars.stream()
                .map(bar -> new IndexQuote.Point(bar.date(), bar.close()))
                .toList();
        PriceBar latest = bars.getLast();
        BigDecimal change = null;
        BigDecimal percent = null;
        if (bars.size() >= 2) {
            BigDecimal previous = bars.get(bars.size() - 2).close();
            change = latest.close().subtract(previous);
            percent = change.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
        }
        return new IndexQuote(index.code(), index.displayName(), index.note(), index.market().name(),
                latest.date(), latest.close(), change, percent, points);
    }

    private static IndexQuote empty(MarketIndex index) {
        return new IndexQuote(index.code(), index.displayName(), index.note(), index.market().name(),
                null, null, null, null, List.of());
    }
}
