package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockSplitDao;
import com.ezstock.dto.CompareRequest;
import com.ezstock.dto.CompareResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
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

class BacktestServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate START = LocalDate.of(2026, 1, 2);
    private static final BigDecimal CAPITAL = new BigDecimal("100000");

    private final Stock a = new Stock("AAAA", "甲股", StockType.STOCK, "測試");
    private final Stock b = new Stock("BBBB", "乙 ETF", StockType.ETF, "測試");

    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private DividendDao dividendDao;
    private StockSplitDao stockSplitDao;
    private PriceLoader priceLoader;
    private BacktestServiceImpl service;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        dividendDao = mock(DividendDao.class);
        stockSplitDao = mock(StockSplitDao.class);
        priceLoader = mock(PriceLoader.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneId.of("Asia/Taipei"));
        // 資料載入器用真的，只把它底下的 DAO 換成 Mockito 假物件
        service = new BacktestServiceImpl(
                new BacktestDataLoader(stockDao, dailyPriceDao, dividendDao, stockSplitDao, priceLoader, clock));

        when(stockDao.findById("AAAA")).thenReturn(Optional.of(a));
        when(stockDao.findById("BBBB")).thenReturn(Optional.of(b));
        when(stockDao.findById("9999")).thenReturn(Optional.empty());
        when(dailyPriceDao.findByStockAndDateRange("AAAA", START, TODAY))
                .thenReturn(List.of(price(a, START, "100"), price(a, START.plusDays(1), "120")));
        when(dailyPriceDao.findByStockAndDateRange("BBBB", START, TODAY))
                .thenReturn(List.of(price(b, START, "50"), price(b, START.plusDays(1), "45")));
        when(dividendDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
        when(stockSplitDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("每一檔都先確保價格已載入，結果順序跟請求一樣，重複的代號只算一次")
    void compareInOrder() {
        CompareResponse response = service.compare(request(List.of("AAAA", "BBBB", "AAAA"), null, false));

        assertThat(response.results()).extracting("stockId").containsExactly("AAAA", "BBBB");
        assertThat(response.results().get(0).metrics().totalReturnPercent()).isEqualByComparingTo("20.00");
        assertThat(response.results().get(1).metrics().totalReturnPercent()).isEqualByComparingTo("-10.00");
        assertThat(response.endDate()).isEqualTo(TODAY);
        verify(priceLoader, times(1)).ensureLoaded("AAAA");
        verify(priceLoader, times(1)).ensureLoaded("BBBB");
    }

    @Test
    @DisplayName("沒指定時預設股利再投入、計算手續費；也會列出回測假設")
    void defaults() {
        CompareResponse response = service.compare(request(List.of("AAAA"), null, null));

        assertThat(response.reinvestDividends()).isTrue();
        assertThat(response.costDescription()).contains("0.1425%");
        assertThat(response.results().getFirst().totalCosts()).isPositive();
        assertThat(response.assumptions()).anyMatch(text -> text.contains("開盤價買進"));
    }

    @Test
    @DisplayName("不計成本時，手續費是 0")
    void withoutCosts() {
        CompareResponse response = service.compare(request(List.of("AAAA"), null, false));

        assertThat(response.costDescription()).isEqualTo("不計交易成本");
        assertThat(response.results().getFirst().totalCosts()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("第一筆價格比起始日晚很多（當時可能還沒上市），要在假設裡提醒")
    void lateStartWarning() {
        LocalDate listed = START.plusMonths(3);
        when(dailyPriceDao.findByStockAndDateRange("BBBB", START, TODAY))
                .thenReturn(List.of(price(b, listed, "50"), price(b, listed.plusDays(1), "51")));

        CompareResponse response = service.compare(request(List.of("BBBB"), null, false));

        assertThat(response.results().getFirst().buyDate()).isEqualTo(listed);
        assertThat(response.assumptions()).anyMatch(text -> text.contains("最早的價格是 " + listed));
    }

    @Test
    @DisplayName("查無股票回 404，而且不會去外部載入")
    void notFound() {
        assertThatThrownBy(() -> service.compare(request(List.of("9999"), null, false)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(priceLoader, never()).ensureLoaded(anyString());
    }

    @Test
    @DisplayName("日期不合理、或本金買不起 1 股，丟出 BadRequestException")
    void badRequests() {
        assertThatThrownBy(() -> service.compare(request(List.of("AAAA"), START, false)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("必須早於");
        assertThatThrownBy(() -> service.compare(new CompareRequest(
                List.of("AAAA"), TODAY.minusYears(11), null, CAPITAL, null, false, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("最多");
        assertThatThrownBy(() -> service.compare(new CompareRequest(
                List.of("AAAA"), START, null, new BigDecimal("50"), null, false, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("不足以");
    }

    private CompareRequest request(List<String> ids, LocalDate end, Boolean includeCosts) {
        return new CompareRequest(ids, START, end, CAPITAL, null, includeCosts, null);
    }

    private DailyPrice price(Stock stock, LocalDate date, String value) {
        BigDecimal v = new BigDecimal(value);
        return new DailyPrice(stock, date, v, v, v, v, 1_000L);
    }
}
