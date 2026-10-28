package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.service.ImportService;
import com.ezstock.service.PriceLoader;
import com.ezstock.service.StockImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按需載入：使用者第一次查詢某檔還沒有價格的股票時，才去 FinMind 抓。
 * 這樣不用一開始就匯入全部 1600 檔，也能查到任何一檔上市股票。
 */
@Service
public class OnDemandPriceLoader implements PriceLoader {

    private static final Logger log = LoggerFactory.getLogger(OnDemandPriceLoader.class);

    private final DailyPriceDao dailyPriceDao;
    private final ImportService importService;

    /** 每檔股票一把鎖：同一檔同時被查兩次時，只有一個請求會去匯入，另一個等它完成 */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /** 這次程式執行期間已經匯入過、但 FinMind 也沒有資料的股票，不再重複呼叫 */
    private final Set<String> noDataStocks = ConcurrentHashMap.newKeySet();

    public OnDemandPriceLoader(DailyPriceDao dailyPriceDao, ImportService importService) {
        this.dailyPriceDao = dailyPriceDao;
        this.importService = importService;
    }

    @Override
    public void ensureLoaded(String stockId) {
        // 大部分情況資料已經有了，先不加鎖檢查一次，比較快
        if (hasPrices(stockId)) {
            return;
        }

        synchronized (locks.computeIfAbsent(stockId, key -> new Object())) {
            // 拿到鎖之後再檢查一次：可能剛剛另一個請求已經載入完了
            if (hasPrices(stockId)) {
                log.debug("按需載入 {}：已由其他請求載入完成", stockId);
                return;
            }
            if (noDataStocks.contains(stockId)) {
                log.debug("按需載入 {}：先前已確認外部資料來源沒有資料，略過", stockId);
                return;
            }

            log.info("按需載入 {}：資料庫尚無價格，開始從外部資料來源載入", stockId);
            StockImportResult result = importService.importStock(stockId);
            if (result.pricesInserted() == 0) {
                noDataStocks.add(stockId);
                log.warn("按需載入 {}：外部資料來源沒有這檔的價格資料", stockId);
            } else {
                log.info("按需載入 {} 完成：日線 {} 筆、股利 {} 筆",
                        stockId, result.pricesInserted(), result.dividendsInserted());
            }
        }
    }

    private boolean hasPrices(String stockId) {
        return dailyPriceDao.findLatest(stockId).isPresent();
    }
}
