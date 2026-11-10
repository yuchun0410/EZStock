package com.ezstock.service.impl;

import com.ezstock.datasource.ExchangeRate;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.finmind.FinMindApiException;
import com.ezstock.service.FxRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedExchangeRateServiceTest {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    private MarketDataSource dataSource;
    private MutableClock clock;
    private CachedExchangeRateService service;

    @BeforeEach
    void setUp() {
        dataSource = mock(MarketDataSource.class);
        clock = new MutableClock(Instant.parse("2026-11-10T02:00:00Z"));
        service = new CachedExchangeRateService(dataSource, clock);
    }

    @Test
    @DisplayName("取最新一天的即期中價：(買入 + 賣出) / 2")
    void latestMidRate() {
        when(dataSource.fetchExchangeRates(eq("USD"), any(), any())).thenReturn(List.of(
                rate(DAY.minusDays(1), "32.72", "32.82"), rate(DAY, "32.84", "32.94")));

        Optional<FxRate> rate = service.latestUsdTwd();

        assertThat(rate).get().satisfies(r -> {
            assertThat(r.rate()).isEqualByComparingTo("32.89");
            assertThat(r.date()).isEqualTo(DAY);
        });
    }

    @Test
    @DisplayName("3 小時內用快取，不重複呼叫外部 API；超過才重新抓")
    void cachesForTtl() {
        when(dataSource.fetchExchangeRates(eq("USD"), any(), any())).thenReturn(List.of(rate(DAY, "32.84", "32.94")));

        service.latestUsdTwd();
        clock.advance(Duration.ofHours(2));
        service.latestUsdTwd();
        verify(dataSource, times(1)).fetchExchangeRates(eq("USD"), any(), any());

        clock.advance(Duration.ofHours(2));
        service.latestUsdTwd();
        verify(dataSource, times(2)).fetchExchangeRates(eq("USD"), any(), any());
    }

    @Test
    @DisplayName("重新抓失敗：先用舊的匯率；10 分鐘內不再重試")
    void staleWhenRefreshFails() {
        when(dataSource.fetchExchangeRates(eq("USD"), any(), any()))
                .thenReturn(List.of(rate(DAY, "32.84", "32.94")))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));
        service.latestUsdTwd();

        clock.advance(Duration.ofHours(4));
        assertThat(service.latestUsdTwd()).get().extracting(FxRate::rate).isEqualTo(new BigDecimal("32.8900"));
        clock.advance(Duration.ofMinutes(5));
        service.latestUsdTwd();

        verify(dataSource, times(2)).fetchExchangeRates(eq("USD"), any(), any());
    }

    @Test
    @DisplayName("第一次就抓不到：回傳 empty，不丟例外")
    void emptyWhenNeverFetched() {
        when(dataSource.fetchExchangeRates(eq("USD"), any(), any()))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));

        assertThat(service.latestUsdTwd()).isEmpty();
    }

    private ExchangeRate rate(LocalDate date, String buy, String sell) {
        return new ExchangeRate("USD", date, new BigDecimal(buy), new BigDecimal(sell));
    }

    /** 可以手動往後撥的時鐘，測試快取有沒有過期 */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return TAIPEI;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
