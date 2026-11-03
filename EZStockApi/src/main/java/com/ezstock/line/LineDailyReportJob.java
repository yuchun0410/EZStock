package com.ezstock.line;

import com.ezstock.dao.MemberDao;
import com.ezstock.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每日回報排程：週一到週五 18:00（台灣時間），等 17:30 的股價更新跑完，推播給每個綁定 LINE 的會員。
 * 某一位失敗只記錄、繼續推下一位。推播會用掉每月的免費額度（一人一天一則）。
 * ezstock.line.report-enabled=false 可以關掉；時間用 ezstock.line.report-cron 調整。
 */
@Component
@ConditionalOnProperty(name = "ezstock.line.report-enabled", havingValue = "true", matchIfMissing = true)
public class LineDailyReportJob {

    private static final Logger log = LoggerFactory.getLogger(LineDailyReportJob.class);

    private final MemberDao memberDao;
    private final LineDailyReportService reportService;
    private final LineMessagingClient messagingClient;
    private final LineProperties properties;

    public LineDailyReportJob(MemberDao memberDao, LineDailyReportService reportService,
                              LineMessagingClient messagingClient, LineProperties properties) {
        this.memberDao = memberDao;
        this.reportService = reportService;
        this.messagingClient = messagingClient;
        this.properties = properties;
        log.info("[每日回報] 排程已啟用");
    }

    @Scheduled(cron = "${ezstock.line.report-cron:0 0 18 * * MON-FRI}", zone = "Asia/Taipei")
    public void run() {
        if (!properties.enabled()) {
            log.warn("[每日回報] 沒有設定 LINE，略過");
            return;
        }
        long started = System.currentTimeMillis();
        List<Member> members = memberDao.findAllLinkedToLine();
        log.info("[每日回報] 開始，共 {} 位會員綁定 LINE", members.size());
        int sent = 0;
        for (Member member : members) {
            try {
                if (messagingClient.push(member.getLineUserId(), reportService.build(member))) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("[每日回報] 會員 id={} 產生回報失敗：{}", member.getId(), e.getMessage(), e);
            }
        }
        log.info("[每日回報] 完成：成功 {} 位、失敗 {} 位，耗時 {} ms",
                sent, members.size() - sent, System.currentTimeMillis() - started);
    }
}
