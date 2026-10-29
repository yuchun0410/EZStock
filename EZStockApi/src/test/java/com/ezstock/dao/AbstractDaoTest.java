package com.ezstock.dao;

import com.ezstock.model.DailyPrice;
import com.ezstock.model.Dividend;
import com.ezstock.model.Member;
import com.ezstock.model.Stock;
import com.ezstock.model.SplitType;
import com.ezstock.model.StockSplit;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA 與 MyBatis 共用的測試案例。
 * 子類別用 ezstock.dao.type 決定要測哪一種實作。
 * 每個測試都在交易內執行，結束後自動回滾，不會留下測試資料。
 *
 * 注意：@Transactional 一定要放在「宣告測試方法的類別」上。
 * Spring 判斷要不要開交易，是看方法宣告在哪個類別，
 * 放在子類別上，父類別繼承下來的測試方法不會被包進交易。
 */
@Transactional
abstract class AbstractDaoTest {

    private static final String TEST_STOCK_ID = "TEST01";

    @Autowired
    protected StockDao stockDao;

    @Autowired
    protected DailyPriceDao dailyPriceDao;

    @Autowired
    protected DividendDao dividendDao;

    @Autowired
    protected StockSplitDao stockSplitDao;

    @Autowired
    protected MemberDao memberDao;

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

        assertThat(stockDao.search("測試股", 20))
                .extracting(Stock::getStockId)
                .contains(TEST_STOCK_ID);

        stockDao.save(new Stock(TEST_STOCK_ID, "改名後", StockType.ETF, "測試產業"));
        Stock updated = stockDao.findById(TEST_STOCK_ID).orElseThrow();
        assertThat(updated.getName()).isEqualTo("改名後");
        assertThat(updated.getType()).isEqualTo(StockType.ETF);
    }

    @Test
    @DisplayName("股票：搜尋筆數由資料庫限制，依代號排序")
    void searchLimit() {
        stockDao.save(new Stock("TEST02", "測試股票二", StockType.STOCK, "測試產業"));

        assertThat(stockDao.search("測試股票", 1))
                .extracting(Stock::getStockId)
                .containsExactly(TEST_STOCK_ID);
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

        // 同一檔有兩天的價格，代號只出現一次
        assertThat(dailyPriceDao.findStockIdsWithPrices()).containsOnlyOnce(TEST_STOCK_ID);
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

    @Test
    @DisplayName("股票分割：批次新增後可以依期間查詢，股數比例修正成整數")
    void stockSplit() {
        LocalDate splitDate = LocalDate.of(2025, 6, 18);
        int inserted = stockSplitDao.insertAll(List.of(new StockSplit(
                stock, splitDate, SplitType.SPLIT, new BigDecimal("188.65"), new BigDecimal("47.16"))));

        assertThat(inserted).isEqualTo(1);
        assertThat(stockSplitDao.findByStockAndDateRange(TEST_STOCK_ID,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.getSplitDate()).isEqualTo(splitDate);
                    assertThat(s.getSplitType()).isEqualTo(SplitType.SPLIT);
                    assertThat(s.getBeforePrice()).isEqualByComparingTo("188.65");
                    assertThat(s.shareRatio()).isEqualByComparingTo("4");
                });
    }

    @Test
    @DisplayName("會員：新增後拿到 id，Email 不分大小寫，綁定 Google 與登入時間可以更新")
    void member() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 11, 0);
        Member created = memberDao.save(Member.withPassword(" Test.User@Example.com ", "$2a$10$hash", "測試會員", now));

        assertThat(created.getId()).isNotNull();
        Member found = memberDao.findByEmail("TEST.USER@example.COM").orElseThrow();
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getEmail()).isEqualTo("test.user@example.com");
        assertThat(found.hasPassword()).isTrue();
        assertThat(found.getGoogleSub()).isNull();

        found.linkGoogle("google-sub-123", "https://example.com/a.png");
        found.recordLogin(now.plusHours(1));
        memberDao.save(found);

        Member linked = memberDao.findByGoogleSub("google-sub-123").orElseThrow();
        assertThat(linked.getId()).isEqualTo(created.getId());
        assertThat(linked.getAvatarUrl()).isEqualTo("https://example.com/a.png");
        assertThat(linked.getLastLoginAt()).isEqualTo(now.plusHours(1));
        assertThat(memberDao.findById(created.getId())).isPresent();
        assertThat(memberDao.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("會員：Google 登入建立的會員沒有密碼")
    void googleMember() {
        Member created = memberDao.save(Member.withGoogle("g@example.com", "google-sub-456", "Google 會員",
                "https://example.com/g.png", LocalDateTime.of(2026, 10, 29, 11, 0)));

        Member found = memberDao.findByGoogleSub("google-sub-456").orElseThrow();
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.hasPassword()).isFalse();
        assertThat(found.getDisplayName()).isEqualTo("Google 會員");
    }

    private DailyPrice price(LocalDate date, String open, String close) {
        BigDecimal o = new BigDecimal(open);
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(stock, date, o, o.max(c), o.min(c), c, 1_000L);
    }
}
