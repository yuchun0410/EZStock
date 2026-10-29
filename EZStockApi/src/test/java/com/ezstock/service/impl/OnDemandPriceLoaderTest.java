package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.datasource.finmind.FinMindApiException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import com.ezstock.service.ImportService;
import com.ezstock.service.StockImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnDemandPriceLoaderTest {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    /** 2026-10-28（週三）台灣時間 10:00，收盤資料還沒出來，應該要有的最新交易日是 10-27 */
    private static final Clock WEDNESDAY_MORNING = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), TAIPEI);

    private DailyPriceDao dailyPriceDao;
    private ImportService importService;
    private OnDemandPriceLoader loader;

    @BeforeEach
    void setUp() {
        dailyPriceDao = mock(DailyPriceDao.class);
        importService = mock(ImportService.class);
        loader = new OnDemandPriceLoader(dailyPriceDao, importService, WEDNESDAY_MORNING);
    }

    @Test
    @DisplayName("已經有最新的價格：直接返回，不呼叫匯入也不更新")
    void alreadyLoaded() {
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.of(priceOn(LocalDate.of(2026, 10, 27))));

        loader.ensureLoaded("2330");

        verify(importService, never()).importStock("2330");
        verify(importService, never()).updateStock("2330");
    }

    @Test
    @DisplayName("有價格但不是最新的：補抓最新資料，3 小時內不重複補")
    void staleIsRefreshed() {
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.of(priceOn(LocalDate.of(2026, 10, 23))));
        when(importService.updateStock("2330")).thenReturn(new StockImportResult("2330", 2, 0, 0, 0, 0));

        loader.ensureLoaded("2330");
        loader.ensureLoaded("2330");

        verify(importService, times(1)).updateStock("2330");
    }

    @Test
    @DisplayName("補抓最新資料失敗：不丟例外，繼續用資料庫裡的舊資料")
    void refreshFailureIsIgnored() {
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.of(priceOn(LocalDate.of(2026, 10, 23))));
        when(importService.updateStock("2330")).thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));

        assertThatNoException().isThrownBy(() -> loader.ensureLoaded("2330"));
    }

    @Test
    @DisplayName("應該要有的最新交易日：16:00 前是前一天，週末往前推到週五")
    void expectedLatestTradingDay() {
        assertThat(loader.expectedLatestTradingDay()).isEqualTo(LocalDate.of(2026, 10, 27));

        Clock wednesdayEvening = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), TAIPEI);    // 17:00
        assertThat(new OnDemandPriceLoader(dailyPriceDao, importService, wednesdayEvening)
                .expectedLatestTradingDay()).isEqualTo(LocalDate.of(2026, 10, 28));

        Clock sunday = Clock.fixed(Instant.parse("2026-11-01T06:00:00Z"), TAIPEI);
        assertThat(new OnDemandPriceLoader(dailyPriceDao, importService, sunday)
                .expectedLatestTradingDay()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    @DisplayName("沒有價格：呼叫匯入")
    void loadWhenMissing() {
        when(dailyPriceDao.findLatest("2308")).thenReturn(Optional.empty());
        when(importService.importStock("2308")).thenReturn(new StockImportResult("2308", 1200, 0, 20, 0, 0));

        loader.ensureLoaded("2308");

        verify(importService).importStock("2308");
    }

    @Test
    @DisplayName("外部資料來源也沒有資料：記下來，之後不再重複呼叫")
    void noDataIsRemembered() {
        when(dailyPriceDao.findLatest("9999")).thenReturn(Optional.empty());
        when(importService.importStock("9999")).thenReturn(new StockImportResult("9999", 0, 0, 0, 0, 0));

        loader.ensureLoaded("9999");
        loader.ensureLoaded("9999");

        verify(importService, times(1)).importStock("9999");
    }

    @Test
    @DisplayName("外部資料來源失敗：例外往外丟，下次查詢會再試一次")
    void failureIsRetried() {
        when(dailyPriceDao.findLatest("2308")).thenReturn(Optional.empty());
        when(importService.importStock("2308")).thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));

        assertThatThrownBy(() -> loader.ensureLoaded("2308")).isInstanceOf(FinMindApiException.class);
        assertThatThrownBy(() -> loader.ensureLoaded("2308")).isInstanceOf(FinMindApiException.class);

        verify(importService, times(2)).importStock("2308");
    }

    private DailyPrice priceOn(LocalDate date) {
        Stock stock = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
        BigDecimal p = new BigDecimal("100");
        return new DailyPrice(stock, date, p, p, p, p, 1L);
    }
}
