package com.ezstock.job;

import com.ezstock.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日更新：週一到週五收盤後，把資料庫裡每一檔股票補上最新的日線、股利、分割。
 * 時間用 ezstock.update.cron 設定，預設 17:30（台灣時間）；ezstock.update.enabled=false 可以關掉。
 */
@Component
@ConditionalOnProperty(name = "ezstock.update.enabled", havingValue = "true", matchIfMissing = true)
public class DailyPriceUpdateJob {

    private static final Logger log = LoggerFactory.getLogger(DailyPriceUpdateJob.class);

    private final ImportService importService;

    public DailyPriceUpdateJob(ImportService importService) {
        this.importService = importService;
        log.info("每日更新排程已啟用");
    }

    @Scheduled(cron = "${ezstock.update.cron:0 30 17 * * MON-FRI}", zone = "Asia/Taipei")
    public void run() {
        log.info("每日更新排程觸發");
        try {
            importService.updateAll();
        } catch (Exception e) {
            // 排程裡的例外如果不接住，只會印在 Spring 自己的 log，這裡記清楚一點
            log.error("每日更新排程失敗：{}", e.getMessage(), e);
        }
    }
}
