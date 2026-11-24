package com.ezstock.job;

import com.ezstock.dao.StockDao;
import com.ezstock.model.Stock;
import com.ezstock.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 股票清單（台股上市櫃、美股）的更新：
 * <ul>
 *   <li>啟動時更新一次台股清單：只呼叫 FinMind 一次，沒變動的不會重寫，部署後就會補上新增的股票（例如加入上櫃股）</li>
 *   <li>啟動時資料庫裡還沒有任何美股，就匯入一次美股清單（美股有好幾千檔，平常只靠每週更新）</li>
 *   <li>每週六台股、美股都更新一次，補上新上市、改名的股票</li>
 * </ul>
 * 只更新清單，不抓價格：價格等使用者第一次查詢時才按需載入。
 * ezstock.import.us-stock-list=false 可以關掉整個排程（測試時關掉，避免連外部 API；設定名稱沿用舊的，不用改設定檔）。
 */
@Component
@ConditionalOnProperty(name = "ezstock.import.us-stock-list", havingValue = "true", matchIfMissing = true)
public class StockListJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockListJob.class);

    private final StockDao stockDao;
    private final ImportService importService;

    public StockListJob(StockDao stockDao, ImportService importService) {
        this.stockDao = stockDao;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        refreshTw();
        long usCount = stockDao.findAll().stream().filter(Stock::isUs).count();
        if (usCount > 0) {
            log.info("[股票清單] 資料庫已有 {} 檔美股，啟動時不重新匯入美股清單", usCount);
            return;
        }
        log.info("[股票清單] 資料庫還沒有美股，啟動時匯入一次");
        refreshUs();
    }

    @Scheduled(cron = "${ezstock.import.us-stock-list-cron:0 0 10 * * SAT}", zone = "Asia/Taipei")
    public void refresh() {
        refreshTw();
        refreshUs();
    }

    /** 清單抓不到不影響其他功能：已經有的股票照常可以查，下次再試 */
    private void refreshTw() {
        try {
            importService.importTwStockList();
        } catch (Exception e) {
            log.error("[股票清單] 台股清單更新失敗：{}", e.getMessage(), e);
        }
    }

    private void refreshUs() {
        try {
            importService.importUsStockList();
        } catch (Exception e) {
            log.error("[股票清單] 美股清單更新失敗：{}", e.getMessage(), e);
        }
    }
}
