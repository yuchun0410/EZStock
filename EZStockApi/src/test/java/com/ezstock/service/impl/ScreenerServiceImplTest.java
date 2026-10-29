package com.ezstock.service.impl;

import com.ezstock.backtest.strategy.ConditionType;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.ConditionSpec;
import com.ezstock.dto.ScreenRequest;
import com.ezstock.dto.ScreenResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.DailyPrice;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenerServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final List<ConditionSpec> ABOVE_MA5 =
            List.of(new ConditionSpec(ConditionType.CLOSE_ABOVE_MOVING_AVERAGE, new BigDecimal("5")));

    private final Stock up = new Stock("AAAA", "上漲股", StockType.STOCK, "測試");
    private final Stock down = new Stock("BBBB", "下跌股", StockType.STOCK, "測試");
    private final Stock young = new Stock("CCCC", "新股", StockType.STOCK, "測試");

    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private PriceLoader priceLoader;
    private ScreenerServiceImpl service;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        priceLoader = mock(PriceLoader.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new ScreenerServiceImpl(stockDao, dailyPriceDao, priceLoader, clock);

        when(stockDao.findById(anyString())).thenReturn(Optional.empty());
        stub(up, "10", "11", "12", "13", "14", "15");
        stub(down, "15", "14", "13", "12", "11", "10");
        stub(young, "10", "11", "12");
    }

    @Test
    @DisplayName("掃描全部：列出符合的，資料不足的列在略過，而且不逐檔補抓")
    void screenAll() {
        when(dailyPriceDao.findStockIdsWithPrices()).thenReturn(List.of("AAAA", "BBBB", "CCCC"));

        ScreenResponse response = service.screen(new ScreenRequest(null, ABOVE_MA5));

        assertThat(response.conditionDescription()).isEqualTo("收盤站上 5 日線（週線）");
        assertThat(response.scannedCount()).isEqualTo(2);
        assertThat(response.matches()).singleElement().satisfies(match -> {
            assertThat(match.stockId()).isEqualTo("AAAA");
            assertThat(match.date()).isEqualTo(TODAY);
            assertThat(match.close()).isEqualByComparingTo("15");
            assertThat(match.change()).isEqualByComparingTo("1");
            assertThat(match.changePercent()).isEqualByComparingTo("7.14");
        });
        assertThat(response.skipped()).singleElement().asString().contains("CCCC", "資料不足");
        verify(priceLoader, never()).ensureLoaded(anyString());
    }

    @Test
    @DisplayName("指定股票（自選股）：會先補抓價格，重複的代號只算一次，查無的列在略過")
    void screenSelected() {
        ScreenResponse response = service.screen(new ScreenRequest(List.of("AAAA", " AAAA ", "ZZZZ"), ABOVE_MA5));

        verify(priceLoader).ensureLoaded("AAAA");
        verify(dailyPriceDao, never()).findStockIdsWithPrices();
        assertThat(response.scannedCount()).isEqualTo(1);
        assertThat(response.matches()).extracting("stockId").containsExactly("AAAA");
        assertThat(response.skipped()).singleElement().asString().contains("ZZZZ", "查無股票");
    }

    @Test
    @DisplayName("某一檔補抓失敗：略過那一檔，其他照常判斷")
    void loaderFailure() {
        doThrow(new IllegalStateException("FinMind 503")).when(priceLoader).ensureLoaded("BBBB");

        ScreenResponse response = service.screen(new ScreenRequest(List.of("AAAA", "BBBB"), ABOVE_MA5));

        assertThat(response.matches()).extracting("stockId").containsExactly("AAAA");
        assertThat(response.skipped()).singleElement().asString().contains("BBBB", "暫時無法取得");
    }

    @Test
    @DisplayName("只能當賣出的條件（停利）不能拿來選股")
    void exitOnlyCondition() {
        ScreenRequest request = new ScreenRequest(null,
                List.of(new ConditionSpec(ConditionType.TAKE_PROFIT, new BigDecimal("10"))));

        assertThatThrownBy(() -> service.screen(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("只能當賣出條件");
    }

    private void stub(Stock stock, String... closes) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate first = TODAY.minusDays(closes.length - 1);
        for (int i = 0; i < closes.length; i++) {
            BigDecimal c = new BigDecimal(closes[i]);
            prices.add(new DailyPrice(stock, first.plusDays(i), c, c, c, c, 1_000L));
        }
        when(stockDao.findById(stock.getStockId())).thenReturn(Optional.of(stock));
        when(dailyPriceDao.findByStockAndDateRange(eq(stock.getStockId()), any(), any())).thenReturn(prices);
    }
}
