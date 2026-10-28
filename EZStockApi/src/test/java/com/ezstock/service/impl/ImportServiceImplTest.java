package com.ezstock.service.impl;

import com.ezstock.config.ImportProperties;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.DividendDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockSplitDao;
import com.ezstock.datasource.CashDividend;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.ShareSplit;
import com.ezstock.datasource.StockInfo;
import com.ezstock.datasource.finmind.FinMindApiException;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.SplitType;
import com.ezstock.model.Stock;
import com.ezstock.model.StockSplit;
import com.ezstock.model.StockType;
import com.ezstock.service.ImportResult;
import com.ezstock.service.StockImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用 Mockito 模擬資料來源與 DAO，只測匯入流程本身的邏輯，不連資料庫、不連 FinMind。
 */
class ImportServiceImplTest {

    /** 固定「今天」是 2026-10-28，匯入期間就會是 2021-10-28 ~ 2026-10-28 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private MarketDataSource dataSource;
    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private DividendDao dividendDao;
    private StockSplitDao stockSplitDao;

    /** 記錄每次 insertAll 收到的資料，方便檢查到底新增了什麼 */
    private final List<DailyPrice> insertedPrices = new ArrayList<>();
    private final List<Dividend> insertedDividends = new ArrayList<>();
    private final List<StockSplit> insertedSplits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = mock(MarketDataSource.class);
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        dividendDao = mock(DividendDao.class);
        stockSplitDao = mock(StockSplitDao.class);

        when(stockDao.findById(anyString())).thenAnswer(inv ->
                Optional.of(new Stock(inv.getArgument(0), "測試", StockType.STOCK, "測試產業")));
        when(dailyPriceDao.findTradeDates(anyString(), any(), any())).thenReturn(List.of());
        when(dividendDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
        when(dataSource.fetchPrices(anyString(), any(), any())).thenReturn(List.of());
        when(dataSource.fetchCashDividends(anyString(), any(), any())).thenReturn(List.of());
        when(dataSource.fetchShareSplits(anyString(), any(), any())).thenReturn(List.of());
        when(stockSplitDao.findByStockAndDateRange(anyString(), any(), any())).thenReturn(List.of());
        when(stockSplitDao.insertAll(anyList())).thenAnswer(inv -> {
            List<StockSplit> splits = inv.getArgument(0);
            insertedSplits.addAll(splits);
            return splits.size();
        });

        when(dailyPriceDao.insertAll(anyList())).thenAnswer(inv -> {
            List<DailyPrice> prices = inv.getArgument(0);
            insertedPrices.addAll(prices);
            return prices.size();
        });
        when(dividendDao.insertAll(anyList())).thenAnswer(inv -> {
            List<Dividend> dividends = inv.getArgument(0);
            insertedDividends.addAll(dividends);
            return dividends.size();
        });
    }

    private ImportServiceImpl createService(List<String> stockIds) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneId.of("Asia/Taipei"));
        return new ImportServiceImpl(dataSource, stockDao, dailyPriceDao, dividendDao, stockSplitDao,
                new ImportProperties(stockIds, 5, false),
                mock(PlatformTransactionManager.class), clock);
    }

    @Test
    @DisplayName("單檔匯入：只新增資料庫還沒有的日期，已存在的略過")
    void importStockSkipsExisting() {
        LocalDate d1 = LocalDate.of(2026, 10, 26);
        LocalDate d2 = LocalDate.of(2026, 10, 27);
        LocalDate exDate = LocalDate.of(2026, 6, 12);
        when(dataSource.fetchPrices(eq("2330"), any(), any())).thenReturn(List.of(bar(d1), bar(d2)));
        when(dataSource.fetchCashDividends(eq("2330"), any(), any()))
                .thenReturn(List.of(new CashDividend("2330", exDate, new BigDecimal("5.0"))));
        // 資料庫裡已經有 d1 的日線、也已經有這筆股利
        when(dailyPriceDao.findTradeDates(eq("2330"), any(), any())).thenReturn(List.of(d1));
        Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
        when(dividendDao.findByStockAndDateRange(eq("2330"), any(), any()))
                .thenReturn(List.of(new Dividend(tsmc, exDate, new BigDecimal("5.0"))));

        StockImportResult result = createService(List.of("2330")).importStock("2330");

        assertThat(insertedPrices).extracting(DailyPrice::getTradeDate).containsExactly(d2);
        assertThat(insertedDividends).isEmpty();
        assertThat(result).isEqualTo(new StockImportResult("2330", 1, 1, 0, 1, 0));
    }

    @Test
    @DisplayName("單檔匯入：股票分割也一起存，資料庫已經有的日期略過")
    void importStockSavesSplits() {
        LocalDate oldSplit = LocalDate.of(2023, 1, 10);
        LocalDate newSplit = LocalDate.of(2025, 6, 18);
        when(dataSource.fetchShareSplits(eq("0050"), any(), any())).thenReturn(List.of(
                new ShareSplit("0050", oldSplit, SplitType.SPLIT, new BigDecimal("100"), new BigDecimal("50")),
                new ShareSplit("0050", newSplit, SplitType.SPLIT, new BigDecimal("188.65"), new BigDecimal("47.16"))));
        Stock etf = new Stock("0050", "元大台灣50", StockType.ETF, "ETF");
        when(stockSplitDao.findByStockAndDateRange(eq("0050"), any(), any())).thenReturn(List.of(
                new StockSplit(etf, oldSplit, SplitType.SPLIT, new BigDecimal("100"), new BigDecimal("50"))));

        StockImportResult result = createService(List.of("0050")).importStock("0050");

        assertThat(insertedSplits).extracting(StockSplit::getSplitDate).containsExactly(newSplit);
        assertThat(result.splitsInserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("單檔匯入：股票分割抓取失敗時，價格和股利照樣匯入")
    void importStockWhenSplitFetchFails() {
        LocalDate d1 = LocalDate.of(2026, 10, 27);
        when(dataSource.fetchPrices(eq("2330"), any(), any())).thenReturn(List.of(bar(d1)));
        when(dataSource.fetchShareSplits(eq("2330"), any(), any()))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));

        StockImportResult result = createService(List.of("2330")).importStock("2330");

        assertThat(result.pricesInserted()).isEqualTo(1);
        assertThat(result.splitsInserted()).isZero();
    }

    @Test
    @DisplayName("單檔匯入：期間是今天往前推設定的年數")
    void importStockUsesConfiguredYears() {
        createService(List.of("2330")).importStock("2330");

        verify(dataSource).fetchPrices("2330", TODAY.minusYears(5), TODAY);
        verify(dataSource).fetchCashDividends("2330", TODAY.minusYears(5), TODAY);
    }

    @Test
    @DisplayName("單檔匯入：股票不在清單中時丟出例外，不寫入任何資料")
    void importStockNotInList() {
        when(stockDao.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createService(List.of("9999")).importStock("9999"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("9999");
        verify(dailyPriceDao, never()).insertAll(anyList());
    }

    @Test
    @DisplayName("全部匯入：股票清單全部存，某一檔失敗不影響其他檔")
    void importAllContinuesAfterFailure() {
        when(dataSource.fetchStockInfos()).thenReturn(List.of(
                new StockInfo("2330", "台積電", StockType.STOCK, "半導體業"),
                new StockInfo("2317", "鴻海", StockType.STOCK, "其他電子業"),
                new StockInfo("0050", "元大台灣50", StockType.ETF, "ETF")));
        when(dataSource.fetchPrices(eq("2317"), any(), any()))
                .thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));
        when(dataSource.fetchPrices(eq("0050"), any(), any()))
                .thenReturn(List.of(bar(LocalDate.of(2026, 10, 27))));

        ImportResult result = createService(List.of("2330", "2317", "0050")).importAll();

        verify(stockDao, times(3)).save(any(Stock.class));
        assertThat(result.stockListCount()).isEqualTo(3);
        assertThat(result.failed()).containsExactly("2317");
        assertThat(result.succeeded()).extracting(StockImportResult::stockId).containsExactly("2330", "0050");
        assertThat(result.totalPricesInserted()).isEqualTo(1);
    }

    private PriceBar bar(LocalDate date) {
        BigDecimal p = new BigDecimal("100.00");
        return new PriceBar("2330", date, p, p, p, p, 1_000L);
    }
}
