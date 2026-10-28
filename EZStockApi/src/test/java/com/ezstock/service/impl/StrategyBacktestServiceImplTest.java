package com.ezstock.service.impl;

import com.ezstock.backtest.strategy.ConditionType;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockSplitDao;
import com.ezstock.dto.ConditionSpec;
import com.ezstock.dto.ConditionTypeInfo;
import com.ezstock.dto.StrategyRequest;
import com.ezstock.dto.StrategyResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyBacktestServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate START = LocalDate.of(2026, 1, 2);
    private static final BigDecimal CAPITAL = new BigDecimal("100000");

    private final Stock stock = new Stock("AAAA", "甲股", StockType.STOCK, "測試");

    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private StrategyBacktestServiceImpl service;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        DividendDao dividendDao = mock(DividendDao.class);
        StockSplitDao stockSplitDao = mock(StockSplitDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new StrategyBacktestServiceImpl(new BacktestDataLoader(
                stockDao, dailyPriceDao, dividendDao, stockSplitDao, mock(PriceLoader.class), clock));

        when(stockDao.findById("AAAA")).thenReturn(Optional.of(stock));
        when(dividendDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
        when(stockSplitDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
        // 第 3 天收盤 95 跌破前 2 日低點 → 第 4 天開盤 100 買進
        // 第 5 天收盤 111，比買進價 100 漲 11% → 第 6 天開盤 112 停利賣出
        when(dailyPriceDao.findByStockAndDateRange("AAAA", START, TODAY)).thenReturn(prices(
                "100", "100", "95", "100", "111", "112"));
    }

    @Test
    @DisplayName("條件回測：照條件買賣，並附上一直持有的對照組和回測假設")
    void run() {
        StrategyResponse response = service.run(request(
                List.of(spec(ConditionType.CLOSE_BELOW_LOWEST_LOW, "2")),
                List.of(spec(ConditionType.TAKE_PROFIT, "10"), spec(ConditionType.STOP_LOSS, "5"))));

        assertThat(response.entryDescription()).isEqualTo("收盤跌破前 2 日最低價");
        assertThat(response.exitDescription()).isEqualTo("漲 10% 停利，或跌 5% 停損");
        assertThat(response.strategy().tradeCount()).isEqualTo(1);
        assertThat(response.strategy().trades().getFirst().exitReason()).isEqualTo("漲 10% 停利");
        assertThat(response.benchmark().stockId()).isEqualTo("AAAA");
        assertThat(response.assumptions()).anyMatch(text -> text.contains("隔天以開盤價成交"));
    }

    @Test
    @DisplayName("規則不合理（停利當買進條件）時回 400，而且不會去撈資料")
    void invalidRule() {
        assertThatThrownBy(() -> service.run(request(
                List.of(spec(ConditionType.TAKE_PROFIT, "10")),
                List.of(spec(ConditionType.STOP_LOSS, "5")))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("只能當賣出條件");
        verify(stockDao, never()).findById(anyString());
    }

    @Test
    @DisplayName("數值超出範圍時回 400")
    void valueOutOfRange() {
        assertThatThrownBy(() -> service.run(request(
                List.of(spec(ConditionType.CLOSE_ABOVE_MOVING_AVERAGE, "999")),
                List.of(spec(ConditionType.STOP_LOSS, "5")))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1～240");
    }

    @Test
    @DisplayName("條件種類清單：每一種都有名稱、單位、預設值")
    void conditionTypes() {
        List<ConditionTypeInfo> types = service.conditionTypes();

        assertThat(types).hasSize(ConditionType.values().length);
        assertThat(types).filteredOn(t -> t.type() == ConditionType.STOP_LOSS).singleElement()
                .satisfies(t -> {
                    assertThat(t.unit()).isEqualTo("%");
                    assertThat(t.entryAllowed()).isFalse();
                });
    }

    private StrategyRequest request(List<ConditionSpec> entry, List<ConditionSpec> exit) {
        return new StrategyRequest("AAAA", START, null, CAPITAL, entry, exit, false, null);
    }

    private ConditionSpec spec(ConditionType type, String value) {
        return new ConditionSpec(type, new BigDecimal(value));
    }

    /** 每天開高低收都一樣 */
    private List<DailyPrice> prices(String... closes) {
        List<DailyPrice> list = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            BigDecimal c = new BigDecimal(closes[i]);
            list.add(new DailyPrice(stock, START.plusDays(i), c, c, c, c, 1_000L));
        }
        return list;
    }
}
