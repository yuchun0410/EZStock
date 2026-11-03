package com.ezstock.line;

import com.ezstock.backtest.strategy.ConditionType;
import com.ezstock.dto.ConditionSpec;
import com.ezstock.dto.ScreenMatch;
import com.ezstock.dto.ScreenRequest;
import com.ezstock.dto.ScreenResponse;
import com.ezstock.dto.WatchlistItemResponse;
import com.ezstock.model.Member;
import com.ezstock.service.AssetService;
import com.ezstock.service.ScreenerService;
import com.ezstock.service.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日回報的內容：
 * 1. 淨資產明細（跟 LINE 傳「資產」一樣）
 * 2. 自選股裡今天「收盤站上月線」的股票（用條件選股的程式）
 * 排程推播與 LINE 傳「回報」共用這裡，傳「回報」用 reply 不花推播額度，方便測試。
 */
@Service
public class LineDailyReportService {

    private static final Logger log = LoggerFactory.getLogger(LineDailyReportService.class);
    static final ConditionSpec ABOVE_MONTHLY_LINE =
            new ConditionSpec(ConditionType.CLOSE_ABOVE_MOVING_AVERAGE, BigDecimal.valueOf(20));

    private final AssetService assetService;
    private final WatchlistService watchlistService;
    private final ScreenerService screenerService;
    private final LineMessageFormatter formatter;
    private final Clock clock;

    public LineDailyReportService(AssetService assetService, WatchlistService watchlistService,
                                  ScreenerService screenerService, LineMessageFormatter formatter, Clock clock) {
        this.assetService = assetService;
        this.watchlistService = watchlistService;
        this.screenerService = screenerService;
        this.formatter = formatter;
        this.clock = clock;
    }

    /** 一則推播最多 5 個訊息框，這裡最多 2 個；一個人一次推播只算一則額度 */
    public List<String> build(Member member) {
        List<String> messages = new ArrayList<>();
        messages.add(formatter.assetReport(member.getDisplayName(), LocalDate.now(clock),
                assetService.summary(member.getId())));

        List<String> watchlist = watchlistService.list(member.getId()).stream()
                .map(WatchlistItemResponse::stockId).toList();
        if (!watchlist.isEmpty()) {
            try {
                ScreenResponse screen = screenerService.screen(new ScreenRequest(watchlist, List.of(ABOVE_MONTHLY_LINE)));
                messages.add(screenMessage(screen, watchlist.size()));
            } catch (Exception e) {
                // 選股失敗不影響資產回報
                log.warn("[每日回報] 會員 id={} 自選股選股失敗：{}", member.getId(), e.getMessage());
            }
        }
        return messages;
    }

    static String screenMessage(ScreenResponse screen, int watchlistSize) {
        StringBuilder text = new StringBuilder("【自選股】").append(screen.conditionDescription())
                .append("：").append(screen.matches().size()).append(" / ").append(watchlistSize).append(" 檔");
        if (screen.matches().isEmpty()) {
            text.append("\n今天沒有符合的股票。");
        }
        for (ScreenMatch match : screen.matches()) {
            text.append('\n').append(match.stockId()).append(' ').append(match.name())
                    .append(' ').append(LineMessageFormatter.money2(match.close()));
            if (match.changePercent() != null) {
                text.append("（").append(LineMessageFormatter.signedPercent(match.changePercent())).append("）");
            }
        }
        return text.toString();
    }
}
