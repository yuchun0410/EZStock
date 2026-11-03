package com.ezstock.news;

import com.ezstock.dao.StockDao;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.NewsItem;
import com.ezstock.dto.NewsResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * 個股相關新聞：首頁（熱門股 + 自選股）和個股頁都用這個。
 *
 * FinMind 一次只能查「一檔、一天」，所以 N 檔股票 × 幾天就要呼叫好幾次：
 * 1. 先查 Redis 快取，今天的新聞存 30 分鐘、過去的存 12 小時
 * 2. 沒快取的用虛擬執行緒（virtual thread）同時去抓，但最多同時 4 個，避免一下子把 FinMind 的額度打爆
 * 3. 合併：同一則新聞（同一個連結）出現在好幾檔股票，只留一筆並列出所有相關股票
 * 4. 依時間由新到舊排序，取前 limit 筆
 */
@Service
public class NewsService {

    static final int MAX_STOCKS = 20;
    static final int MAX_LIMIT = 50;
    static final int MAX_CONCURRENT_CALLS = 4;
    static final Duration TODAY_TTL = Duration.ofMinutes(30);
    static final Duration PAST_TTL = Duration.ofHours(12);

    private static final String STOCK_ID_PATTERN = "\\d{4}[0-9A-Z]{0,2}";
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final MarketDataSource dataSource;
    private final NewsCache cache;
    private final StockDao stockDao;
    private final Clock clock;
    private final Semaphore finMindPermits = new Semaphore(MAX_CONCURRENT_CALLS);

    public NewsService(MarketDataSource dataSource, NewsCache cache, StockDao stockDao, Clock clock) {
        this.dataSource = dataSource;
        this.cache = cache;
        this.stockDao = stockDao;
        this.clock = clock;
    }

    /**
     * 只查一檔時往前看 3 天（個股頁，新聞少的股票才不會空白）；查很多檔時只看今天和昨天（首頁，數量已經夠多）。
     */
    public List<NewsResponse> latest(List<String> requestedIds, int limit) {
        Map<String, String> names = resolveStocks(requestedIds);
        int days = names.size() == 1 ? 3 : 2;
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        LocalDate today = LocalDate.now(clock);

        long start = System.currentTimeMillis();
        List<NewsItem> all = fetchAll(names.keySet(), today, days);

        // 用連結當作同一則新聞的判斷，LinkedHashMap 保留第一次出現的順序
        Map<String, List<NewsItem>> byLink = new LinkedHashMap<>();
        for (NewsItem item : all) {
            byLink.computeIfAbsent(item.link(), link -> new ArrayList<>()).add(item);
        }

        List<NewsResponse> result = byLink.values().stream()
                .map(items -> toResponse(items, names))
                .sorted(Comparator.comparing(NewsResponse::publishedAt).reversed())
                .limit(size)
                .toList();
        log.info("[新聞] {} 檔股票、{} 天：共 {} 則（合併重複後 {} 則），回傳 {} 則，耗時 {} ms",
                names.size(), days, all.size(), byLink.size(), result.size(), System.currentTimeMillis() - start);
        return result;
    }

    /** 檢查代號格式、去掉重複、最多 20 檔，再到資料庫確認股票存在、順便拿名稱 */
    private Map<String, String> resolveStocks(List<String> requestedIds) {
        Set<String> ids = new LinkedHashSet<>();
        if (requestedIds != null) {
            for (String id : requestedIds) {
                if (id != null && id.strip().matches(STOCK_ID_PATTERN)) {
                    ids.add(id.strip());
                }
            }
        }
        if (ids.isEmpty()) {
            throw new BadRequestException("請至少指定 1 檔股票");
        }
        if (ids.size() > MAX_STOCKS) {
            throw new BadRequestException("一次最多查 " + MAX_STOCKS + " 檔股票的新聞");
        }
        Map<String, String> names = stockDao.findByIds(ids).stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName, (a, b) -> a, LinkedHashMap::new));
        if (names.isEmpty()) {
            throw new BadRequestException("查無這些股票");
        }
        return names;
    }

    private List<NewsItem> fetchAll(Set<String> stockIds, LocalDate today, int days) {
        List<NewsItem> all = new ArrayList<>();
        List<Future<List<NewsItem>>> pending = new ArrayList<>();
        int cacheHits = 0;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String stockId : stockIds) {
                for (int i = 0; i < days; i++) {
                    LocalDate date = today.minusDays(i);
                    var cached = cache.get(stockId, date);
                    if (cached.isPresent()) {
                        cacheHits++;
                        all.addAll(cached.get());
                    } else {
                        pending.add(executor.submit(() -> fetchAndCache(stockId, date, today)));
                    }
                }
            }
            for (Future<List<NewsItem>> future : pending) {
                all.addAll(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[新聞] 抓取被中斷，回傳目前拿到的部分");
        } catch (ExecutionException e) {
            // fetchAndCache 自己會接住 FinMind 的錯誤，走到這裡是非預期的錯
            log.error("[新聞] 抓取時發生非預期錯誤：{}", e.getCause().getMessage(), e.getCause());
        }
        log.debug("[新聞] 快取命中 {} 筆，向 FinMind 抓 {} 筆", cacheHits, pending.size());
        return all;
    }

    /**
     * 抓一檔一天的新聞並存進快取。抓失敗（例如 FinMind 額度用完）只記 WARN、回傳空的，
     * 而且不存快取，下次還會再試；一檔失敗不影響其他檔。
     */
    private List<NewsItem> fetchAndCache(String stockId, LocalDate date, LocalDate today) throws InterruptedException {
        finMindPermits.acquire();
        try {
            List<NewsItem> news = dataSource.fetchNews(stockId, date);
            cache.put(stockId, date, news, date.equals(today) ? TODAY_TTL : PAST_TTL);
            return news;
        } catch (RuntimeException e) {
            log.warn("[新聞] {} {} 抓取失敗，略過：{}", stockId, date, e.getMessage());
            return List.of();
        } finally {
            finMindPermits.release();
        }
    }

    private static NewsResponse toResponse(List<NewsItem> items, Map<String, String> names) {
        NewsItem first = items.getFirst();
        List<NewsResponse.RelatedStock> stocks = items.stream()
                .map(NewsItem::stockId)
                .distinct()
                .map(id -> new NewsResponse.RelatedStock(id, names.get(id)))
                .toList();
        return new NewsResponse(first.title(), first.source(), first.link(), first.publishedAt(), stocks);
    }
}
