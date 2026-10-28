package com.ezstock.job;

import com.ezstock.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ezstock.import.run-on-startup=true 時，啟動完成後自動匯入一次。
 * 之後做好 API，會改成由管理員呼叫 POST /api/admin/import 觸發。
 */
@Component
@ConditionalOnProperty(name = "ezstock.import.run-on-startup", havingValue = "true")
public class StartupImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupImportRunner.class);

    private final ImportService importService;

    public StartupImportRunner(ImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("ezstock.import.run-on-startup=true，啟動時自動匯入");
        try {
            importService.importAll();
        } catch (Exception e) {
            // 匯入失敗不要讓整個程式掛掉，記錄下來即可
            log.error("啟動時自動匯入失敗：{}", e.getMessage(), e);
        }
    }
}
