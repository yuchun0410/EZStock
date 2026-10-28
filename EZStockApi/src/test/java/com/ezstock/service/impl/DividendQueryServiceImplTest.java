package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.DividendRecord;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import com.ezstock.service.PriceLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DividendQueryServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");

    private StockDao stockDao;
    private DividendDao dividendDao;
    private DailyPriceDao dailyPriceDao;
    private PriceLoader priceLoader;
    private DividendQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dividendDao = mock(DividendDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        priceLoader = mock(PriceLoader.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new DividendQueryServiceImpl(stockDao, dividendDao, dailyPriceDao, priceLoader, clock);

        when(stockDao.findById("2330")).thenReturn(Optional.of(tsmc));
        when(stockDao.findById("9999")).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("殖利率用除息前一個交易日的收盤計算，結果由新到舊")
    void yieldUsesCloseBeforeExDate() {
        LocalDate ex1 = LocalDate.of(2026, 3, 17);  // 週二，前一個交易日是週一 3/16
        LocalDate ex2 = LocalDate.of(2026, 6, 16);  // 週二，前一個交易日是週五 6/12（週一 6/15 沒有資料）
        when(dividendDao.findByStockAndDateRange("2330", TODAY.minusYears(10), TODAY))
                .thenReturn(List.of(dividend(ex1, "5.0000"), dividend(ex2, "6.0000")));
        when(dailyPriceDao.findByStockAndDateRange("2330", ex1.minusDays(20), ex2))
                .thenReturn(List.of(
                        price(LocalDate.of(2026, 3, 16), "1000.00"),
                        price(ex1, "990.00"),
                        price(LocalDate.of(2026, 6, 12), "1200.00"),
                        price(ex2, "1190.00")));

        List<DividendRecord> result = service.getDividends("2330");

        assertThat(result).extracting(DividendRecord::exDate).containsExactly(ex2, ex1);
        DividendRecord june = result.get(0);
        assertThat(june.closeBeforeExDate()).isEqualByComparingTo("1200.00");
        // 6 / 1200 * 100 = 0.5
        assertThat(june.yieldPercent()).isEqualByComparingTo("0.50");
        DividendRecord march = result.get(1);
        assertThat(march.closeBeforeExDate()).isEqualByComparingTo("1000.00");
        assertThat(march.yieldPercent()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("價格只查一次，不會每筆股利各查一次（避免 N+1）")
    void pricesQueriedOnce() {
        when(dividendDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of(
                dividend(LocalDate.of(2025, 3, 18), "4.5000"),
                dividend(LocalDate.of(2025, 6, 12), "4.5000"),
                dividend(LocalDate.of(2025, 9, 16), "5.0000"),
                dividend(LocalDate.of(2025, 12, 11), "5.0000")));
        when(dailyPriceDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());

        service.getDividends("2330");

        verify(dailyPriceDao, times(1)).findByStockAndDateRange(anyString(), any(), any());
    }

    @Test
    @DisplayName("找不到除息前的收盤價時，收盤與殖利率是 null")
    void noPriceBeforeExDate() {
        LocalDate ex = LocalDate.of(2021, 11, 1);
        when(dividendDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of(dividend(ex, "2.7500")));
        when(dailyPriceDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of(price(ex, "600.00")));

        DividendRecord record = service.getDividends("2330").getFirst();

        assertThat(record.cashDividend()).isEqualByComparingTo("2.75");
        assertThat(record.closeBeforeExDate()).isNull();
        assertThat(record.yieldPercent()).isNull();
    }

    @Test
    @DisplayName("沒有除息紀錄時回傳空清單，也不查價格")
    void noDividends() {
        when(dividendDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());

        assertThat(service.getDividends("2330")).isEmpty();
        verify(dailyPriceDao, never()).findByStockAndDateRange(anyString(), any(), any());
    }

    @Test
    @DisplayName("查無股票丟出 ResourceNotFoundException，也不會去外部載入")
    void notFound() {
        assertThatThrownBy(() -> service.getDividends("9999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");
        verify(priceLoader, never()).ensureLoaded(anyString());
    }

    private Dividend dividend(LocalDate exDate, String cash) {
        return new Dividend(tsmc, exDate, new BigDecimal(cash));
    }

    private DailyPrice price(LocalDate date, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(tsmc, date, c, c, c, c, 1_000L);
    }
}
