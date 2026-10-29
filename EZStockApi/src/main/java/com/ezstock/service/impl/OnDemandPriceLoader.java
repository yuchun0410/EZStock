package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.model.DailyPrice;
import com.ezstock.service.ImportService;
import com.ezstock.service.PriceLoader;
import com.ezstock.service.StockImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按需載入：
 * <ul>
 *   <li>使用者第一次查詢某檔還沒有價格的股票時，才去 FinMind 抓。不用一開始就匯入全部 1600 檔</li>
 *   <li>已經有價格、但不是最新的（例如昨天收盤的還沒抓），順便補上最新資料</li>
 * </ul>
 */
@Service
public class OnDemandPriceLoader implements PriceLoader {

    private static final Logger log = LoggerFactory.getLogger(OnDemandPriceLoader.class);

    /** 台股 13:30 收盤，外部資料來源大約下午才更新，16:00 之後才預期有今天的資料 */
    static final LocalTime DATA_READY_TIME = LocalTime.of(16, 0);

    /** 同一檔股票補資料後，至少隔這麼久才會再試（例如遇到國定假日，今天本來就不會有資料） */
    static final Duration REFRESH_INTERVAL = Duration.ofHours(3);

    private final DailyPriceDao dailyPriceDao;
    private final ImportService importService;
    private final Clock clock;

    /** 每檔股票一把鎖：同一檔同時被查兩次時，只有一個請求會去匯入，另一個等它完成 */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /** 這次程式執行期間已經匯入過、但 FinMind 也沒有資料的股票，不再重複呼叫 */
    private final Set<String> noDataStocks = ConcurrentHashMap.newKeySet();

    /** 每檔股票上一次嘗試補資料的時間 */
    private final ConcurrentHashMap<String, Instant> lastRefresh = new ConcurrentHashMap<>();

    public OnDemandPriceLoader(DailyPriceDao dailyPriceDao, ImportService importService, Clock clock) {
        this.dailyPriceDao = dailyPriceDao;
        this.importService = importService;
        this.clock = clock;
    }

    @Override
    public void ensureLoaded(String stockId) {
        // 大部分情況資料已經有了，先不加鎖檢查一次，比較快
        Optional<DailyPrice> latest = dailyPriceDao.findLatest(stockId);
        if (latest.isPresent()) {
            refreshIfStale(stockId, latest.get().getTradeDate());
            return;
        }
        synchronized (lockOf(stockId)) {
            // 拿到鎖之後再檢查一次：可能剛剛另一個請求已經載入完了
            if (dailyPriceDao.findLatest(stockId).isPresent()) {
                log.debug("按需載入 {}：已由其他請求載入完成", stockId);
                return;
            }
            if (noDataStocks.contains(stockId)) {
                log.debug("按需載入 {}：先前已確認外部資料來源沒有資料，略過", stockId);
                return;
            }
            log.info("按需載入 {}：資料庫尚無價格，開始從外部資料來源載入", stockId);
            StockImportResult result = importService.importStock(stockId);
            lastRefresh.put(stockId, clock.instant());
            if (result.pricesInserted() == 0) {
                noDataStocks.add(stockId);
                log.warn("按需載入 {}：外部資料來源沒有這檔的價格資料", stockId);
            } else {
                log.info("按需載入 {} 完成：日線 {} 筆、股利 {} 筆",
                        stockId, result.pricesInserted(), result.dividendsInserted());
            }
        }
    }

    /**
     * 資料庫最新一筆比「應該要有的最新交易日」舊，就補資料。
     * 補資料失敗不往外丟：舊資料還是可以用，不要因為外部 API 暫時有問題就讓整個查詢失敗。
     */
    private void refreshIfStale(String stockId, LocalDate latestDate) {
        LocalDate expected = expectedLatestTradingDay();
        if (!latestDate.isBefore(expected) || recentlyRefreshed(stockId)) {
            return;
        }
        synchronized (lockOf(stockId)) {
            if (recentlyRefreshed(stockId)) {
                return;
            }
            lastRefresh.put(stockId, clock.instant());
            log.info("按需更新 {}：資料庫最新是 {}，應該要有 {} 的資料，補抓最新資料", stockId, latestDate, expected);
            try {
                StockImportResult result = importService.updateStock(stockId);
                log.info("按需更新 {} 完成：日線新增 {} 筆、股利 {} 筆", stockId,
                        result.pricesInserted(), result.dividendsInserted());
            } catch (Exception e) {
                log.warn("按需更新 {} 失敗，先用資料庫裡的資料：{}", stockId, e.getMessage());
            }
        }
    }

    /** 現在應該要有的最新交易日：16:00 之前是前一天；週末往前推到週五（國定假日不判斷，靠 REFRESH_INTERVAL 避免一直重試） */
    LocalDate expectedLatestTradingDay() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate date = now.toLocalTime().isBefore(DATA_READY_TIME) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }

    private boolean recentlyRefreshed(String stockId) {
        Instant last = lastRefresh.get(stockId);
        return last != null && Duration.between(last, clock.instant()).compareTo(REFRESH_INTERVAL) < 0;
    }

    private Object lockOf(String stockId) {
        return locks.computeIfAbsent(stockId, key -> new Object());
    }
}
