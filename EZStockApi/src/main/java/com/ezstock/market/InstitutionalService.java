package com.ezstock.market;

import com.ezstock.datasource.InstitutionalTrade;
import com.ezstock.datasource.MarketDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首頁的三大法人買賣金額：取最近一個交易日，整理成外資、投信、自營商三類。
 * 外部資料把外資、自營商各拆成兩項，這裡合併：
 * <ul>
 *   <li>外資 = 外資及陸資（Foreign_Investor）+ 外資自營商（Foreign_Dealer_Self）</li>
 *   <li>自營商 = 自行買賣（Dealer_self）+ 避險（Dealer_Hedging）</li>
 * </ul>
 * 一天才更新一次，記在記憶體裡 30 分鐘；抓失敗先用舊的資料。
 */
@Service
public class InstitutionalService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalService.class);

    static final Duration TTL = Duration.ofMinutes(30);
    /** 往前抓幾天：遇到連假也一定有最近一個交易日 */
    static final int LOOKBACK_DAYS = 10;

    /** 顯示名稱 → 外部資料的法人名稱 */
    private static final Map<String, Set<String>> GROUPS = Map.of(
            "外資", Set.of("Foreign_Investor", "Foreign_Dealer_Self"),
            "投信", Set.of("Investment_Trust"),
            "自營商", Set.of("Dealer_self", "Dealer_Hedging"));
    private static final List<String> ORDER = List.of("外資", "投信", "自營商");

    private final MarketDataSource marketDataSource;
    private final Clock clock;

    private volatile InstitutionalSummary cached;
    private volatile Instant fetchedAt;

    public InstitutionalService(MarketDataSource marketDataSource, Clock clock) {
        this.marketDataSource = marketDataSource;
        this.clock = clock;
    }

    public synchronized InstitutionalSummary latest() {
        if (cached != null && Duration.between(fetchedAt, clock.instant()).compareTo(TTL) < 0) {
            return cached;
        }
        try {
            LocalDate today = LocalDate.now(clock);
            InstitutionalSummary summary = summarize(
                    marketDataSource.fetchInstitutionalTrades(today.minusDays(LOOKBACK_DAYS), today));
            cached = summary;
            fetchedAt = clock.instant();
            log.info("[三大法人] 更新 {}：{}，合計 {}", summary.date(),
                    summary.groups().stream().map(g -> g.name() + " " + g.net()).toList(),
                    summary.total() == null ? null : summary.total().net());
            return summary;
        } catch (Exception e) {
            log.warn("[三大法人] 抓取失敗：{}", e.getMessage());
            if (cached != null) {
                // 先用舊的，5 分鐘後再重試，避免每個請求都去打外部 API
                fetchedAt = clock.instant().minus(TTL).plus(Duration.ofMinutes(5));
                return cached;
            }
            return new InstitutionalSummary(null, List.of(), null);
        }
    }

    /** 只取最新的一天，合併成三類；合計自己加，不依賴外部資料的 total 那一列 */
    static InstitutionalSummary summarize(List<InstitutionalTrade> trades) {
        LocalDate latest = trades.stream().map(InstitutionalTrade::date).max(Comparator.naturalOrder()).orElse(null);
        if (latest == null) {
            return new InstitutionalSummary(null, List.of(), null);
        }
        List<InstitutionalTrade> day = trades.stream().filter(t -> t.date().equals(latest)).toList();
        List<InstitutionalSummary.Group> groups = ORDER.stream().map(name -> {
            Set<String> members = GROUPS.get(name);
            BigDecimal buy = day.stream().filter(t -> members.contains(t.investor()))
                    .map(InstitutionalTrade::buy).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sell = day.stream().filter(t -> members.contains(t.investor()))
                    .map(InstitutionalTrade::sell).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new InstitutionalSummary.Group(name, buy, sell, buy.subtract(sell));
        }).toList();
        BigDecimal buy = groups.stream().map(InstitutionalSummary.Group::buy).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sell = groups.stream().map(InstitutionalSummary.Group::sell).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InstitutionalSummary(latest, groups, new InstitutionalSummary.Group("合計", buy, sell, buy.subtract(sell)));
    }
}
