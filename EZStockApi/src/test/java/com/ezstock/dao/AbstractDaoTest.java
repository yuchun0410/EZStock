package com.ezstock.dao;

import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA 與 MyBatis 共用的測試案例。
 * 子類別用 ezstock.dao.type 決定要測哪一種實作。
 * 每個測試都在交易內執行，結束後自動回滾，不會留下測試資料。
 */
abstract class AbstractDaoTest {

    private static final String TEST_STOCK_ID = "TEST01";

    @Autowired
    protected StockDao stockDao;

    @Autowired
    protected DailyPriceDao dailyPriceDao;

    @Autowired
    protected DividendDao dividendDao;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = new Stock(TEST_STOCK_ID, "測試股票", StockType.STOCK, "測試產業");
        stockDao.save(stock);
    }

    @Test
    @DisplayName("股票：新增後可以用代號查詢、用名稱搜尋，再次儲存會更新")
    void stock() {
        assertThat(stockDao.findById(TEST_STOCK_ID))
                .isPresent()
                .get().extracting(Stock::getName).isEqualTo("測試股票");

        assertThat(stockDao.search("測試股"))
                .extracting(Stock::getStockId)
                .contains(TEST_STOCK_ID);

        stockDao.save(new Stock(TEST_STOCK_ID, "改名後", StockType.ETF, "測試產業"));
        Stock updated = stockDao.findById(TEST_STOCK_ID).orElseThrow();
        assertThat(updated.getName()).isEqualTo("改名後");
        assertThat(updated.getType()).isEqualTo(StockType.ETF);
    }

    @Test
    @DisplayName("日線：批次新增後可以依期間查詢、取最新一筆、取已存在日期")
    void dailyPrice() {
        LocalDate d1 = LocalDate.of(2026, 1, 2);
        LocalDate d2 = LocalDate.of(2026, 1, 5);
        int inserted = dailyPriceDao.insertAll(List.of(
                price(d1, "100.00", "105.50"),
                price(d2, "105.50", "103.00")));

        assertThat(inserted).isEqualTo(2);

        List<DailyPrice> prices = dailyPriceDao.findByStockAndDateRange(TEST_STOCK_ID, d1, d2);
        assertThat(prices).extracting(DailyPrice::getTradeDate).containsExactly(d1, d2);
        assertThat(prices.get(0).getClosePrice()).isEqualByComparingTo("105.50");
        assertThat(prices.get(0).getStock().getStockId()).isEqualTo(TEST_STOCK_ID);

        assertThat(dailyPriceDao.findLatest(TEST_STOCK_ID))
                .isPresent()
                .get().extracting(DailyPrice::getTradeDate).isEqualTo(d2);

        assertThat(dailyPriceDao.findTradeDates(TEST_STOCK_ID, d1, d2))
                .containsExactlyInAnyOrder(d1, d2);
    }

    @Test
    @DisplayName("除息：批次新增後可以依期間查詢")
    void dividend() {
        LocalDate exDate = LocalDate.of(2026, 3, 18);
        int inserted = dividendDao.insertAll(List.of(
                new Dividend(stock, exDate, new BigDecimal("4.5000"))));

        assertThat(inserted).isEqualTo(1);
        assertThat(dividendDao.findByStockAndDateRange(TEST_STOCK_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getExDate()).isEqualTo(exDate);
                    assertThat(d.getCashDividend()).isEqualByComparingTo("4.5");
                });
    }

    private DailyPrice price(LocalDate date, String open, String close) {
        BigDecimal o = new BigDecimal(open);
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(stock, date, o, o.max(c), o.min(c), c, 1_000L);
    }
}
