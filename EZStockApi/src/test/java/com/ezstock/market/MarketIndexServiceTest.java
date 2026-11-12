package com.ezstock.market;

import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.finmind.FinMindApiException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketIndexServiceTest {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final LocalDate D1 = LocalDate.of(2026, 11, 11);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 12);

    private MarketDataSource dataSource;
    private Instant now;
    private MarketIndexService service;

    @BeforeEach
    void setUp() {
        dataSource = mock(MarketDataSource.class);
        now = Instant.parse("2026-11-12T09:00:00Z");
        Clock clock = new Clock() {
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
        };
        service = new MarketIndexService(dataSource, clock);
        when(dataSource.fetchPrices(eq("TAIEX"), any(), any()))
                .thenReturn(List.of(bar("TAIEX", D1, "44257.15"), bar("TAIEX", D2, "43091.87")));
        when(dataSource.fetchUsPrices(anyString(), any(), any()))
                .thenReturn(List.of(bar("^GSPC", D1, "7500"), bar("^GSPC", D2, "7549.94")));
    }

    @Test
    @DisplayName("台股指數用台股的資料集、美股指數用美股的；算出跟前一天比的漲跌")
    void quotes() {
        List<IndexQuote> quotes = service.all();

        assertThat(quotes).extracting(IndexQuote::code).containsExactly("TAIEX", "^DJI", "^GSPC", "^IXIC");
        IndexQuote taiex = quotes.getFirst();
        assertThat(taiex.close()).isEqualByComparingTo("43091.87");
        assertThat(taiex.change()).isEqualByComparingTo("-1165.28");
        assertThat(taiex.changePercent()).isEqualByComparingTo("-2.63");
        assertThat(taiex.points()).hasSize(2);
        verify(dataSource).fetchPrices(eq("TAIEX"), any(), any());
        verify(dataSource).fetchUsPrices(eq("^GSPC"), any(), any());
    }

    @Test
    @DisplayName("30 分鐘內用記憶體裡的資料，不重複呼叫外部 API")
    void cached() {
        service.all();
        now = now.plus(Duration.ofMinutes(20));
        service.all();
        verify(dataSource, times(1)).fetchPrices(eq("TAIEX"), any(), any());

        now = now.plus(Duration.ofMinutes(20));
        service.all();
        verify(dataSource, times(2)).fetchPrices(eq("TAIEX"), any(), any());
    }

    @Test
    @DisplayName("一個指數抓不到：其他指數照常；之前有抓過就先用舊的，沒有就回傳空的")
    void failure() {
        when(dataSource.fetchUsPrices(eq("^DJI"), any(), any()))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));

        List<IndexQuote> quotes = service.all();
        assertThat(quotes.get(1).close()).isNull();
        assertThat(quotes.get(1).points()).isEmpty();
        assertThat(quotes.get(0).close()).isNotNull();

        when(dataSource.fetchPrices(eq("TAIEX"), any(), any()))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));
        now = now.plus(Duration.ofHours(1));
        assertThat(service.all().getFirst().close()).isEqualByComparingTo("43091.87");
    }

    private static PriceBar bar(String id, LocalDate date, String close) {
        BigDecimal c = new BigDecimal(close);
        return new PriceBar(id, date, c, c, c, c, 1_000L);
    }
}
