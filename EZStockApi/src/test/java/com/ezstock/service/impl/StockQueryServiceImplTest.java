package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.PricePoint;
import com.ezstock.dto.StockDetail;
import com.ezstock.dto.StockSummary;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockQueryServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");

    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private StockQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new StockQueryServiceImpl(stockDao, dailyPriceDao, clock);

        when(stockDao.findById("2330")).thenReturn(Optional.of(tsmc));
        when(stockDao.findById("9999")).thenReturn(Optional.empty());
        when(dailyPriceDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("搜尋：去掉前後空白，最多回傳 20 筆")
    void searchLimit() {
        List<Stock> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(new Stock(String.valueOf(2000 + i), "測試" + i, StockType.STOCK, "測試"));
        }
        when(stockDao.search("2")).thenReturn(many);

        List<StockSummary> result = service.search("  2  ");

        assertThat(result).hasSize(StockQueryServiceImpl.SEARCH_LIMIT);
        assertThat(result.get(0).stockId()).isEqualTo("2000");
    }

    @Test
    @DisplayName("搜尋：空白關鍵字丟出 BadRequestException")
    void searchBlank() {
        assertThatThrownBy(() -> service.search("   ")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("個股：用前一個交易日的收盤計算漲跌與漲跌幅")
    void detailChange() {
        LocalDate friday = LocalDate.of(2026, 10, 23);
        LocalDate monday = LocalDate.of(2026, 10, 26);
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.of(price(monday, "1100.00")));
        when(dailyPriceDao.findByStockAndDateRange("2330", monday.minusDays(30), monday.minusDays(1)))
                .thenReturn(List.of(price(LocalDate.of(2026, 10, 22), "1050.00"), price(friday, "1085.00")));

        StockDetail detail = service.getDetail("2330");

        assertThat(detail.latestDate()).isEqualTo(monday);
        assertThat(detail.latestClose()).isEqualByComparingTo("1100.00");
        assertThat(detail.change()).isEqualByComparingTo("15.00");
        // 15 / 1085 * 100 = 1.382... 四捨五入到小數 2 位
        assertThat(detail.changePercent()).isEqualByComparingTo("1.38");
    }

    @Test
    @DisplayName("個股：還沒匯入價格時，行情欄位是 null")
    void detailWithoutPrices() {
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.empty());

        StockDetail detail = service.getDetail("2330");

        assertThat(detail.name()).isEqualTo("台積電");
        assertThat(detail.latestClose()).isNull();
        assertThat(detail.change()).isNull();
    }

    @Test
    @DisplayName("個股：查無股票丟出 ResourceNotFoundException")
    void detailNotFound() {
        assertThatThrownBy(() -> service.getDetail("9999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("日線：沒給日期時，預設查今天往前一年")
    void pricesDefaultRange() {
        when(dailyPriceDao.findByStockAndDateRange("2330", TODAY.minusYears(1), TODAY))
                .thenReturn(List.of(price(TODAY.minusDays(1), "1100.00")));

        List<PricePoint> result = service.getPrices("2330", null, null);

        verify(dailyPriceDao).findByStockAndDateRange("2330", TODAY.minusYears(1), TODAY);
        assertThat(result).singleElement().extracting(PricePoint::close).isEqualTo(new BigDecimal("1100.00"));
    }

    @Test
    @DisplayName("日線：開始日期晚於結束日期、或超過 10 年，丟出 BadRequestException")
    void pricesInvalidRange() {
        assertThatThrownBy(() -> service.getPrices("2330", TODAY, TODAY.minusDays(1)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getPrices("2330", TODAY.minusYears(11), TODAY))
                .isInstanceOf(BadRequestException.class);
    }

    private DailyPrice price(LocalDate date, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(tsmc, date, c, c, c, c, 1_000L);
    }
}
