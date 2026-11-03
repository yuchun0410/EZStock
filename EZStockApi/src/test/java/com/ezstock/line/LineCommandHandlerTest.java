package com.ezstock.line;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.StockDao;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineCommandHandlerTest {

    private final Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private LineCommandHandler handler;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T07:00:00Z"), ZoneId.of("Asia/Taipei"));
        handler = new LineCommandHandler(stockDao, dailyPriceDao, clock);

        when(stockDao.findById(anyString())).thenReturn(Optional.empty());
        when(stockDao.findById("2330")).thenReturn(Optional.of(tsmc));
        when(stockDao.search(anyString(), anyInt())).thenReturn(List.of());
        when(stockDao.search("台積電", 1)).thenReturn(List.of(tsmc));
        when(dailyPriceDao.findByStocksSince(any(), any())).thenReturn(List.of(
                price(LocalDate.of(2026, 10, 29), "2415"),
                price(LocalDate.of(2026, 10, 30), "2370")));
    }

    @Test
    @DisplayName("傳代號或名稱：回收盤價與漲跌")
    void quote() {
        String byId = handler.handle("U1234567890", " 2330 ").get(0);
        String byName = handler.handle("U1234567890", "台積電").get(0);

        assertThat(byId).isEqualTo("2330 台積電\n收盤 2,370.00（10/30）\n漲跌 ▼-45.00（-1.86%）");
        assertThat(byName).isEqualTo(byId);
    }

    @Test
    @DisplayName("說明、看不懂的指令：回說明")
    void help() {
        assertThat(handler.handle("U1", "說明")).containsExactly(LineCommandHandler.HELP);
        assertThat(handler.handle("U1", "今天天氣如何呢")).singleElement().asString()
                .startsWith("看不懂「今天天氣如何呢」").contains(LineCommandHandler.HELP);
    }

    @Test
    @DisplayName("沒有股價資料：告訴使用者")
    void noPrice() {
        when(dailyPriceDao.findByStocksSince(any(), any())).thenReturn(List.of());

        assertThat(handler.handle("U1", "2330")).containsExactly("2330 台積電\n最近沒有股價資料。");
    }

    private DailyPrice price(LocalDate date, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(tsmc, date, c, c, c, c, 1_000L);
    }
}
