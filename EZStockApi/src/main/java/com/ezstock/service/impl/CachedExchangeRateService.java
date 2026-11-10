package com.ezstock.service.impl;

import com.ezstock.datasource.ExchangeRate;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.service.ExchangeRateService;
import com.ezstock.service.FxRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 美元匯率，記在記憶體裡 3 小時：
 * 匯率一天才變幾次，資產頁每次打開都去問外部 API 太浪費（FinMind 有每小時呼叫上限）。
 * 時間到了才重新抓；重抓失敗就先用舊的（匯率差一點點比整個資產頁打不開好），並記 WARN。
 * 只有一台主機，所以放記憶體就夠；之後部署成多台再改用 Redis 共用。
 */
@Service
public class CachedExchangeRateService implements ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(CachedExchangeRateService.class);

    static final String USD = "USD";

    /** 快取多久 */
    static final Duration TTL = Duration.ofHours(3);

    /** 抓失敗後，至少隔這麼久才再試，避免外部 API 出問題時每個請求都去打 */
    static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);

    /** 往前抓幾天：遇到連假也一定抓得到最近一個營業日 */
    static final int LOOKBACK_DAYS = 14;

    private final MarketDataSource marketDataSource;
    private final Clock clock;

    /** 快取的匯率和抓到的時間；用 volatile 讓別的執行緒馬上看得到新值 */
    private volatile Cached cached;

    /** 上一次抓取失敗的時間 */
    private volatile Instant lastFailure;

    private record Cached(FxRate rate, Instant fetchedAt) {
    }

    public CachedExchangeRateService(MarketDataSource marketDataSource, Clock clock) {
        this.marketDataSource = marketDataSource;
        this.clock = clock;
    }

    @Override
    public Optional<FxRate> latestUsdTwd() {
        Cached current = cached;
        if (current != null && Duration.between(current.fetchedAt(), clock.instant()).compareTo(TTL) < 0) {
            return Optional.of(current.rate());
        }
        synchronized (this) {
            // 拿到鎖之後再檢查一次：可能別的請求剛剛已經抓好了
            current = cached;
            if (current != null && Duration.between(current.fetchedAt(), clock.instant()).compareTo(TTL) < 0) {
                return Optional.of(current.rate());
            }
            if (lastFailure != null && Duration.between(lastFailure, clock.instant()).compareTo(RETRY_INTERVAL) < 0) {
                log.debug("[匯率] 剛剛才抓失敗，{} 分鐘內不重試", RETRY_INTERVAL.toMinutes());
                return Optional.ofNullable(current).map(Cached::rate);
            }
            return refresh(current);
        }
    }

    private Optional<FxRate> refresh(Cached stale) {
        LocalDate today = LocalDate.now(clock);
        try {
            List<ExchangeRate> rates = marketDataSource.fetchExchangeRates(USD, today.minusDays(LOOKBACK_DAYS), today);
            if (rates.isEmpty()) {
                log.warn("[匯率] 最近 {} 天沒有美元匯率資料", LOOKBACK_DAYS);
                return fallback(stale);
            }
            ExchangeRate latest = rates.getLast();
            FxRate rate = new FxRate(USD, latest.mid(), latest.date());
            cached = new Cached(rate, clock.instant());
            lastFailure = null;
            log.info("[匯率] 更新美元匯率：{} 即期買入 {}、賣出 {}，中價 {}",
                    latest.date(), latest.spotBuy(), latest.spotSell(), rate.rate());
            return Optional.of(rate);
        } catch (Exception e) {
            log.warn("[匯率] 抓取美元匯率失敗：{}", e.getMessage());
            return fallback(stale);
        }
    }

    private Optional<FxRate> fallback(Cached stale) {
        lastFailure = clock.instant();
        if (stale == null) {
            return Optional.empty();
        }
        log.warn("[匯率] 先用舊的匯率 {}（{}）", stale.rate().rate(), stale.rate().date());
        return Optional.of(stale.rate());
    }
}
