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
 * 美股清單：
 * <ul>
 *   <li>啟動時資料庫裡還沒有任何美股，就自動匯入一次（新環境、正式環境第一次部署都不用手動處理）</li>
 *   <li>每週六更新一次，補上新上市、改名的股票（沒變動的不會重寫）</li>
 * </ul>
 * 只匯入清單，不抓價格：幾千檔美股的價格等使用者第一次查詢時才按需載入。
 * ezstock.import.us-stock-list=false 可以關掉（測試時關掉，避免連外部 API）。
 */
@Component
@ConditionalOnProperty(name = "ezstock.import.us-stock-list", havingValue = "true", matchIfMissing = true)
public class UsStockListJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsStockListJob.class);

    private final StockDao stockDao;
    private final ImportService importService;

    public UsStockListJob(StockDao stockDao, ImportService importService) {
        this.stockDao = stockDao;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long usCount = stockDao.findAll().stream().filter(Stock::isUs).count();
        if (usCount > 0) {
            log.info("[美股清單] 資料庫已有 {} 檔美股，啟動時不重新匯入", usCount);
            return;
        }
        log.info("[美股清單] 資料庫還沒有美股，啟動時匯入一次");
        refresh();
    }

    @Scheduled(cron = "${ezstock.import.us-stock-list-cron:0 0 10 * * SAT}", zone = "Asia/Taipei")
    public void refresh() {
        try {
            importService.importUsStockList();
        } catch (Exception e) {
            // 清單抓不到不影響其他功能：已經有的美股照常可以查，下次再試
            log.error("[美股清單] 匯入失敗：{}", e.getMessage(), e);
        }
    }
}
