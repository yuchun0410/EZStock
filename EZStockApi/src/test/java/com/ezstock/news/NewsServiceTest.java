package com.ezstock.news;

import com.ezstock.dao.StockDao;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.NewsItem;
import com.ezstock.datasource.finmind.FinMindApiException;
import com.ezstock.dto.NewsResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private MarketDataSource dataSource;
    private StockDao stockDao;
    private MemoryCache cache;
    private NewsService service;

    @BeforeEach
    void setUp() {
        dataSource = mock(MarketDataSource.class);
        stockDao = mock(StockDao.class);
        cache = new MemoryCache();
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T06:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new NewsService(dataSource, cache, stockDao, clock);

        when(stockDao.findByIds(anyCollection())).thenAnswer(invocation -> {
            List<Stock> all = List.of(
                    new Stock("2330", "台積電", StockType.STOCK, "半導體業"),
                    new Stock("2317", "鴻海", StockType.STOCK, "其他電子業"));
            return all.stream().filter(s -> invocation.<java.util.Collection<String>>getArgument(0).contains(s.getStockId())).toList();
        });
        when(dataSource.fetchNews(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("合併：同一個連結只留一筆並列出相關股票，依時間由新到舊，取前 limit 筆")
    void mergeAndSort() {
        when(dataSource.fetchNews("2330", TODAY)).thenReturn(List.of(
                news("2330", "09:00", "台積電法說會", "https://news.test/a"),
                news("2330", "13:30", "電子股走強", "https://news.test/shared")));
        when(dataSource.fetchNews("2317", TODAY)).thenReturn(List.of(
                news("2317", "13:30", "電子股走強", "https://news.test/shared")));
        when(dataSource.fetchNews("2317", YESTERDAY)).thenReturn(List.of(
                newsAt("2317", YESTERDAY.atTime(20, 0), "鴻海營收", "https://news.test/b")));

        List<NewsResponse> result = service.latest(List.of("2330", "2317", "2330"), 2);

        assertThat(result).extracting(NewsResponse::title).containsExactly("電子股走強", "台積電法說會");
        assertThat(result.get(0).stocks()).extracting(NewsResponse.RelatedStock::name).containsExactly("台積電", "鴻海");
    }

    @Test
    @DisplayName("快取：今天的存 30 分鐘、昨天的存 12 小時；第二次查不會再打 FinMind")
    void cache() {
        service.latest(List.of("2330", "2317"), 20);
        service.latest(List.of("2330", "2317"), 20);

        verify(dataSource, times(1)).fetchNews("2330", TODAY);
        verify(dataSource, times(1)).fetchNews("2330", YESTERDAY);
        verify(dataSource, never()).fetchNews("2330", TODAY.minusDays(2));
        assertThat(cache.ttls.get("2330:" + TODAY)).isEqualTo(NewsService.TODAY_TTL);
        assertThat(cache.ttls.get("2330:" + YESTERDAY)).isEqualTo(NewsService.PAST_TTL);
    }

    @Test
    @DisplayName("只查一檔（個股頁）往前看 3 天")
    void singleStockLooksBackThreeDays() {
        service.latest(List.of("2330"), 10);

        verify(dataSource).fetchNews("2330", TODAY.minusDays(2));
    }

    @Test
    @DisplayName("FinMind 失敗：那一檔略過、不存快取，其他檔照常回傳")
    void finMindFailure() {
        when(dataSource.fetchNews("2330", TODAY)).thenThrow(new FinMindApiException("FinMind 呼叫失敗：HTTP 402"));
        when(dataSource.fetchNews("2317", TODAY)).thenReturn(List.of(
                news("2317", "10:00", "鴻海新聞", "https://news.test/c")));

        List<NewsResponse> result = service.latest(List.of("2330", "2317"), 20);

        assertThat(result).extracting(NewsResponse::title).containsExactly("鴻海新聞");
        assertThat(cache.get("2330", TODAY)).isEmpty();
    }

    @Test
    @DisplayName("參數檢查：格式不對的代號忽略；全部不合法、超過 20 檔、都查無股票回 400")
    void validation() {
        assertThatThrownBy(() -> service.latest(List.of("abc", "<script>"), 10))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.latest(Collections.nCopies(1, "9999"), 10))
                .isInstanceOf(BadRequestException.class).hasMessage("查無這些股票");
        List<String> tooMany = java.util.stream.IntStream.range(0, 21).mapToObj(i -> String.valueOf(1000 + i)).toList();
        assertThatThrownBy(() -> service.latest(tooMany, 10))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("最多查 20 檔");
    }

    private static NewsItem news(String stockId, String time, String title, String link) {
        return newsAt(stockId, LocalDateTime.parse(TODAY + "T" + time), title, link);
    }

    private static NewsItem newsAt(String stockId, LocalDateTime at, String title, String link) {
        return new NewsItem(stockId, at, title, "測試新聞", link);
    }

    /** 記憶體版快取，順便記下每個 key 的 TTL 給測試檢查 */
    private static class MemoryCache implements NewsCache {
        final Map<String, List<NewsItem>> data = new ConcurrentHashMap<>();
        final Map<String, Duration> ttls = new ConcurrentHashMap<>();

        @Override
        public Optional<List<NewsItem>> get(String stockId, LocalDate date) {
            return Optional.ofNullable(data.get(stockId + ":" + date));
        }

        @Override
        public void put(String stockId, LocalDate date, List<NewsItem> news, Duration ttl) {
            data.put(stockId + ":" + date, news);
            ttls.put(stockId + ":" + date, ttl);
        }
    }
}
