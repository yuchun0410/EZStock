package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.MoneyAccountDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockHoldingDao;
import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dto.HoldingRequest;
import com.ezstock.dto.HoldingResponse;
import com.ezstock.dto.MoneyAccountRequest;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.AccountKind;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Market;
import com.ezstock.model.MoneyAccount;
import com.ezstock.model.Stock;
import com.ezstock.model.StockHolding;
import com.ezstock.model.StockType;
import com.ezstock.service.PriceLoader;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServiceImplTest {

    private static final long ME = 7L;
    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 2, 12, 0);

    private final Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
    private final Stock etf = new Stock("0050", "元大台灣50", StockType.ETF, "ETF");
    private final Stock noPrice = new Stock("9999", "沒股價", StockType.STOCK, "測試");

    private MoneyAccountDao moneyAccountDao;
    private StockHoldingDao stockHoldingDao;
    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private PriceLoader priceLoader;
    private AssetServiceImpl service;

    @BeforeEach
    void setUp() {
        moneyAccountDao = mock(MoneyAccountDao.class);
        stockHoldingDao = mock(StockHoldingDao.class);
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        priceLoader = mock(PriceLoader.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new AssetServiceImpl(moneyAccountDao, stockHoldingDao, stockDao, dailyPriceDao, priceLoader, clock);

        when(moneyAccountDao.findByMember(ME)).thenReturn(List.of(
                account(1L, AccountKind.CASH, "活存", "200000"),
                account(2L, AccountKind.CASH, "定存", "100000.50"),
                account(3L, AccountKind.LIABILITY, "信貸", "150000")));
        when(stockHoldingDao.findByMember(ME)).thenReturn(List.of(
                new StockHolding(ME, tsmc, 100, new BigDecimal("1000"), T),
                new StockHolding(ME, etf, 2000, new BigDecimal("50.5"), T)));
        when(dailyPriceDao.findLatest("2330")).thenReturn(Optional.of(price(tsmc, "1100")));
        when(dailyPriceDao.findLatest("0050")).thenReturn(Optional.of(price(etf, "45.25")));
        when(dailyPriceDao.findLatest("9999")).thenReturn(Optional.empty());
        when(moneyAccountDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("淨資產 = 現金 + 股票現值 − 負債，每檔持股算出現值、損益、報酬率")
    void summary() {
        AssetSummaryResponse summary = service.summary(ME);

        assertThat(summary.cashTotal()).isEqualByComparingTo("300000.50");
        assertThat(summary.liabilityTotal()).isEqualByComparingTo("150000");
        // 台積電 100 股 × 1100 = 110000；0050 2000 股 × 45.25 = 90500
        assertThat(summary.stockValue()).isEqualByComparingTo("200500");
        assertThat(summary.totalAssets()).isEqualByComparingTo("500500.50");
        assertThat(summary.netWorth()).isEqualByComparingTo("350500.50");
        // 成本 100000 + 101000 = 201000，損益 −500
        assertThat(summary.stockCost()).isEqualByComparingTo("201000");
        assertThat(summary.stockUnrealizedPnl()).isEqualByComparingTo("-500");
        assertThat(summary.stockReturnPercent()).isEqualByComparingTo("-0.25");

        HoldingResponse tsmcRow = summary.holdings().get(0);
        assertThat(tsmcRow.marketValue()).isEqualByComparingTo("110000");
        assertThat(tsmcRow.unrealizedPnl()).isEqualByComparingTo("10000");
        assertThat(tsmcRow.returnPercent()).isEqualByComparingTo("10");
        assertThat(tsmcRow.priceDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(summary.holdings().get(1).returnPercent()).isEqualByComparingTo("-10.40");
        assertThat(summary.notes()).isEmpty();
    }

    @Test
    @DisplayName("沒有股價的持股：只顯示成本，不算進現值和損益，並提醒使用者")
    void holdingWithoutPrice() {
        when(stockHoldingDao.findByMember(ME)).thenReturn(List.of(
                new StockHolding(ME, tsmc, 100, new BigDecimal("1000"), T),
                new StockHolding(ME, noPrice, 1000, new BigDecimal("10"), T)));

        AssetSummaryResponse summary = service.summary(ME);

        assertThat(summary.stockValue()).isEqualByComparingTo("110000");
        assertThat(summary.stockCost()).isEqualByComparingTo("100000");
        assertThat(summary.holdings().get(1).marketValue()).isNull();
        assertThat(summary.holdings().get(1).cost()).isEqualByComparingTo("10000");
        assertThat(summary.notes()).singleElement().asString().contains("9999", "沒有股價");
    }

    @Test
    @DisplayName("更新股價失敗：不影響儀表板，用資料庫現有的價格")
    void priceLoaderFailure() {
        doThrow(new IllegalStateException("FinMind 402")).when(priceLoader).ensureLoaded("2330");

        assertThat(service.summary(ME).stockValue()).isEqualByComparingTo("200500");
    }

    @Test
    @DisplayName("什麼都沒有：全部是 0，報酬率是 null（不能除以 0）")
    void empty() {
        when(moneyAccountDao.findByMember(ME)).thenReturn(List.of());
        when(stockHoldingDao.findByMember(ME)).thenReturn(List.of());

        AssetSummaryResponse summary = service.summary(ME);

        assertThat(summary.netWorth()).isEqualByComparingTo("0");
        assertThat(summary.stockReturnPercent()).isNull();
    }

    @Test
    @DisplayName("新增帳戶：一定要選種類；名稱去掉前後空白")
    void createAccount() {
        assertThatThrownBy(() -> service.createAccount(ME, new MoneyAccountRequest(null, "活存", BigDecimal.TEN)))
                .isInstanceOf(BadRequestException.class);

        service.createAccount(ME, new MoneyAccountRequest(AccountKind.CASH, " 郵局 ", new BigDecimal("5000")));
        verify(moneyAccountDao).save(org.mockito.ArgumentMatchers.argThat(a ->
                a.getName().equals("郵局") && a.getKind() == AccountKind.CASH && a.getMemberId() == ME));
    }

    @Test
    @DisplayName("修改、刪除別人的帳戶（或不存在的）：404")
    void otherMembersAccount() {
        when(moneyAccountDao.findByIdAndMember(anyLong(), eq(ME))).thenReturn(Optional.empty());
        when(moneyAccountDao.deleteByIdAndMember(anyLong(), eq(ME))).thenReturn(false);

        assertThatThrownBy(() -> service.updateAccount(ME, 99L,
                new MoneyAccountRequest(null, "偷改", BigDecimal.ONE))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteAccount(ME, 99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(moneyAccountDao, never()).save(any());
    }

    @Test
    @DisplayName("持股：代號轉大寫；查無股票回 404")
    void saveHolding() {
        when(stockDao.findById("2330")).thenReturn(Optional.of(tsmc));
        when(stockDao.findById("ABC")).thenReturn(Optional.empty());

        service.saveHolding(ME, " 2330 ", new HoldingRequest(3000L, new BigDecimal("950")));
        verify(stockHoldingDao).upsert(org.mockito.ArgumentMatchers.argThat(h ->
                h.getId().getStockId().equals("2330") && h.getShares() == 3000));

        assertThatThrownBy(() -> service.saveHolding(ME, "abc", new HoldingRequest(1L, BigDecimal.ONE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("持股：美股要換算匯率，這一版先不開放，回 400 而且不寫入")
    void rejectUsHolding() {
        when(stockDao.findById("AAPL")).thenReturn(Optional.of(
                new Stock("AAPL", "Apple Inc.", StockType.STOCK, Market.US, null)));

        assertThatThrownBy(() -> service.saveHolding(ME, "aapl", new HoldingRequest(10L, new BigDecimal("200"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("美股");
        verify(stockHoldingDao, never()).upsert(any());
    }

    @Test
    @DisplayName("刪除沒有持有的股票：404")
    void deleteMissingHolding() {
        when(stockHoldingDao.delete(anyLong(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.deleteHolding(ME, "2330")).isInstanceOf(ResourceNotFoundException.class);
    }

    private MoneyAccount account(long id, AccountKind kind, String name, String balance) {
        MoneyAccount account = new MoneyAccount(ME, kind, name, new BigDecimal(balance), T);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private DailyPrice price(Stock stock, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(stock, LocalDate.of(2026, 10, 30), c, c, c, c, 1_000L);
    }
}
