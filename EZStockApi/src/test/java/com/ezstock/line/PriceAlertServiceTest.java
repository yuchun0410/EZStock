package com.ezstock.line;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.PriceAlertDao;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.AlertDirection;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.PriceAlert;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceAlertServiceTest {

    private static final long ME = 7L;
    private final Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
    private final Stock etf = new Stock("0050", "元大台灣50", StockType.ETF, "ETF");

    private PriceAlertDao priceAlertDao;
    private DailyPriceDao dailyPriceDao;
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        priceAlertDao = mock(PriceAlertDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new PriceAlertService(priceAlertDao, dailyPriceDao, clock);
        when(priceAlertDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(priceAlertDao.findActiveByMember(ME)).thenReturn(List.of());
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.of(price(tsmc, "2180")));
        when(dailyPriceDao.findLatest("0050")).thenReturn(Optional.of(price(etf, "75")));
    }

    @Test
    @DisplayName("收盤跌破／突破目標價（剛好等於也算）才通知，通知過的標記時間")
    void checkTriggered() {
        PriceAlert below = alert(1L, tsmc, AlertDirection.BELOW, "2200");
        PriceAlert notYet = alert(2L, tsmc, AlertDirection.ABOVE, "2500");
        PriceAlert exactly = alert(3L, etf, AlertDirection.ABOVE, "75");
        when(priceAlertDao.findActiveByMember(ME)).thenReturn(List.of(below, notYet, exactly));

        List<String> lines = service.checkTriggered(ME);

        assertThat(lines).containsExactly(
                "2330 台積電 跌破 2,200.00｜收盤 2,180.00",
                "0050 元大台灣50 突破 75.00｜收盤 75.00");
        assertThat(below.getTriggeredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 18, 0));
        assertThat(notYet.getTriggeredAt()).isNull();
        verify(priceAlertDao, never()).save(notYet);
    }

    @Test
    @DisplayName("新增：回傳目前收盤，已經到價時提醒使用者；目標價要大於 0；最多 20 個")
    void create() {
        PriceAlertService.Created created = service.create(ME, tsmc, AlertDirection.BELOW, new BigDecimal("2200"));
        assertThat(created.latestClose()).isEqualByComparingTo("2180");
        assertThat(created.alreadyReached()).isTrue();

        assertThatThrownBy(() -> service.create(ME, tsmc, AlertDirection.BELOW, BigDecimal.ZERO))
                .isInstanceOf(BadRequestException.class);

        when(priceAlertDao.findActiveByMember(ME))
                .thenReturn(Collections.nCopies(PriceAlertService.MAX_ACTIVE, alert(9L, tsmc, AlertDirection.ABOVE, "1")));
        assertThatThrownBy(() -> service.create(ME, tsmc, AlertDirection.ABOVE, BigDecimal.TEN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最多 20 個");
    }

    @Test
    @DisplayName("刪除：用清單上的編號，超出範圍回 empty")
    void deleteByNumber() {
        PriceAlert first = alert(11L, tsmc, AlertDirection.BELOW, "2200");
        PriceAlert second = alert(12L, etf, AlertDirection.ABOVE, "80");
        when(priceAlertDao.findActiveByMember(ME)).thenReturn(List.of(first, second));

        assertThat(service.deleteByNumber(ME, 2)).contains(second);
        verify(priceAlertDao).deleteByIdAndMember(12L, ME);
        assertThat(service.deleteByNumber(ME, 3)).isEmpty();
        assertThat(service.deleteByNumber(ME, 0)).isEmpty();
    }

    private PriceAlert alert(long id, Stock stock, AlertDirection direction, String target) {
        PriceAlert alert = new PriceAlert(ME, stock, direction, new BigDecimal(target), LocalDateTime.of(2026, 11, 1, 9, 0));
        ReflectionTestUtils.setField(alert, "id", id);
        return alert;
    }

    private static DailyPrice price(Stock stock, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(stock, LocalDate.of(2026, 11, 3), c, c, c, c, 1_000L);
    }
}
