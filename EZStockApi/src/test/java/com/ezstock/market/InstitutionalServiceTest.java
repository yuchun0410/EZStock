package com.ezstock.market;

import com.ezstock.datasource.InstitutionalTrade;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.finmind.FinMindApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionalServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 11);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 12);

    @Test
    @DisplayName("只取最新一天；外資、自營商把兩項合併；買賣超 = 買進 − 賣出；合計是三類加總")
    void summarize() {
        InstitutionalSummary summary = InstitutionalService.summarize(List.of(
                trade(D1, "Foreign_Investor", "999", "1"),
                trade(D2, "Foreign_Investor", "386", "420"),
                trade(D2, "Foreign_Dealer_Self", "1", "0"),
                trade(D2, "Investment_Trust", "50", "10"),
                trade(D2, "Dealer_self", "8", "14"),
                trade(D2, "Dealer_Hedging", "33", "45"),
                trade(D2, "total", "478", "489")));

        assertThat(summary.date()).isEqualTo(D2);
        assertThat(summary.groups()).extracting(InstitutionalSummary.Group::name).containsExactly("外資", "投信", "自營商");
        assertThat(summary.groups().get(0).net()).isEqualByComparingTo("-33");
        assertThat(summary.groups().get(1).net()).isEqualByComparingTo("40");
        assertThat(summary.groups().get(2).net()).isEqualByComparingTo("-18");
        assertThat(summary.total().net()).isEqualByComparingTo("-11");
    }

    @Test
    @DisplayName("30 分鐘內用快取；抓失敗而且沒有舊資料時回傳空的，不丟例外")
    void cacheAndFailure() {
        MarketDataSource dataSource = mock(MarketDataSource.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Asia/Taipei"));
        when(dataSource.fetchInstitutionalTrades(any(), any())).thenReturn(List.of(trade(D2, "Investment_Trust", "50", "10")));
        InstitutionalService service = new InstitutionalService(dataSource, clock);

        service.latest();
        service.latest();
        verify(dataSource, times(1)).fetchInstitutionalTrades(any(), any());

        MarketDataSource failing = mock(MarketDataSource.class);
        when(failing.fetchInstitutionalTrades(any(), any())).thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));
        InstitutionalSummary empty = new InstitutionalService(failing, clock).latest();
        assertThat(empty.date()).isNull();
        assertThat(empty.groups()).isEmpty();
    }

    private static InstitutionalTrade trade(LocalDate date, String investor, String buy, String sell) {
        return new InstitutionalTrade(date, investor, new BigDecimal(buy), new BigDecimal(sell));
    }
}
