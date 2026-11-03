package com.ezstock.line;

import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dto.ScreenMatch;
import com.ezstock.dto.ScreenRequest;
import com.ezstock.dto.ScreenResponse;
import com.ezstock.dto.WatchlistItemResponse;
import com.ezstock.model.Member;
import com.ezstock.model.StockType;
import com.ezstock.service.AssetService;
import com.ezstock.service.ScreenerService;
import com.ezstock.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineDailyReportServiceTest {

    private static final AssetSummaryResponse SUMMARY = new AssetSummaryResponse(
            new BigDecimal("20000"), new BigDecimal("2370000"), new BigDecimal("2390000"),
            new BigDecimal("10000"), new BigDecimal("2380000"), new BigDecimal("950000"),
            new BigDecimal("1420000"), new BigDecimal("149.47"), List.of(), List.of(), List.of());

    private AssetService assetService;
    private WatchlistService watchlistService;
    private ScreenerService screenerService;
    private PriceAlertService alertService;
    private LineDailyReportService service;
    private Member member;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        watchlistService = mock(WatchlistService.class);
        screenerService = mock(ScreenerService.class);
        alertService = mock(PriceAlertService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new LineDailyReportService(assetService, watchlistService, screenerService,
                new LineMessageFormatter(), alertService, clock);
        member = Member.withPassword("a@example.com", "hash", "小明", LocalDateTime.now());
        ReflectionTestUtils.setField(member, "id", 7L);
        when(assetService.summary(7L)).thenReturn(SUMMARY);
    }

    @Test
    @DisplayName("有自選股：資產明細＋自選股站上月線，選股只掃自選股")
    void withWatchlist() {
        when(watchlistService.list(7L)).thenReturn(List.of(
                new WatchlistItemResponse("2330", "台積電", StockType.STOCK, LocalDateTime.now()),
                new WatchlistItemResponse("0050", "元大台灣50", StockType.ETF, LocalDateTime.now())));
        when(screenerService.screen(any())).thenReturn(new ScreenResponse("收盤站上 20 日線（月線）", 2,
                List.of(new ScreenMatch("0050", "元大台灣50", StockType.ETF, "ETF", LocalDate.of(2026, 11, 3),
                        new BigDecimal("75.3"), new BigDecimal("0.5"), new BigDecimal("0.67"))),
                List.of()));

        List<String> messages = service.build(member);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).startsWith("小明 的資產（11/3）");
        assertThat(messages.get(1)).isEqualTo("【自選股】收盤站上 20 日線（月線）：1 / 2 檔\n0050 元大台灣50 75.30（+0.67%）");
        ArgumentCaptor<ScreenRequest> request = ArgumentCaptor.forClass(ScreenRequest.class);
        verify(screenerService).screen(request.capture());
        assertThat(request.getValue().stockIds()).containsExactly("2330", "0050");
    }

    @Test
    @DisplayName("有到價的提醒：放在最前面")
    void triggeredAlertsFirst() {
        when(watchlistService.list(7L)).thenReturn(List.of());
        when(alertService.checkTriggered(7L)).thenReturn(List.of("2330 台積電 跌破 2,200.00｜收盤 2,180.00"));

        List<String> messages = service.build(member);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isEqualTo("【到價提醒】\n2330 台積電 跌破 2,200.00｜收盤 2,180.00");
        assertThat(messages.get(1)).startsWith("小明 的資產");
    }

    @Test
    @DisplayName("沒有自選股：只有資產明細，不跑選股；選股失敗也不影響資產明細")
    void withoutWatchlist() {
        when(watchlistService.list(7L)).thenReturn(List.of());

        assertThat(service.build(member)).hasSize(1);
        verify(screenerService, never()).screen(any());

        when(watchlistService.list(7L)).thenReturn(List.of(
                new WatchlistItemResponse("2330", "台積電", StockType.STOCK, LocalDateTime.now())));
        when(screenerService.screen(any())).thenThrow(new IllegalStateException("FinMind 402"));
        assertThat(service.build(member)).hasSize(1);
    }
}
