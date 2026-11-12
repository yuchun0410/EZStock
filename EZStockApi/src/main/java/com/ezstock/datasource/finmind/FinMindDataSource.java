package com.ezstock.datasource.finmind;

import com.ezstock.datasource.CashDividend;
import com.ezstock.datasource.ExchangeRate;
import com.ezstock.datasource.InstitutionalTrade;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.NewsItem;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.ShareSplit;
import com.ezstock.datasource.StockInfo;
import com.ezstock.model.Market;
import com.ezstock.model.SplitType;
import com.ezstock.model.StockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 透過 FinMind API 取得台股資料。
 * 文件：https://finmind.github.io/quickstart/
 */
@Component
public class FinMindDataSource implements MarketDataSource {

    private static final Logger log = LoggerFactory.getLogger(FinMindDataSource.class);

    /** 上市股票與 ETF 的代號格式：4 碼數字開頭，後面最多 2 碼數字或英文 */
    private static final String LISTED_STOCK_ID = "\\d{4}[0-9A-Z]{0,2}";

    /** 美股代號：1～5 個英文字母；或 BRK/B 這種「代號/股別」，查價格時要寫成 BRK-B */
    private static final Pattern US_TICKER = Pattern.compile("[A-Z]{1,5}|[A-Z]{1,4}/[A-Z]");

    /** 美股公司（非 ETF）至少要這麼大的市值才放進清單：排除幾千檔小型股、SPAC，搜尋結果才不會一堆沒人聽過的 */
    static final BigDecimal US_MIN_MARKET_CAP = new BigDecimal("1000000000");

    /** 名稱有這些字的是特別股、債券、權證、單位，不是普通股，不放進清單 */
    private static final Pattern US_NON_COMMON = Pattern.compile(
            "Preferred|Notes|Debentures|Warrant|Rights?$|Units?$|Unit |Depositary Shares (Each|each|rep)|Trust Securities|Senior|Subordinated|% ");

    /** 名稱結尾的「普通股」說明拿掉，例如 "Apple Inc. Common Stock" → "Apple Inc." */
    private static final Pattern US_NAME_SUFFIX = Pattern.compile(
            "\\s+(Common Stock|Common Shares|Ordinary Shares?|American Depositary Shares?|American Depository Shares?|New York Registry Shares)$",
            Pattern.CASE_INSENSITIVE);

    static final int NAME_MAX_LENGTH = 100;

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final RestClient restClient;
    private final FinMindProperties properties;

    public FinMindDataSource(RestClient finMindRestClient, FinMindProperties properties) {
        this.restClient = finMindRestClient;
        this.properties = properties;
        if (properties.hasToken()) {
            log.info("FinMind 已設定 token");
        } else {
            log.warn("FinMind 未設定 token，使用匿名額度（每小時可呼叫次數較少）");
        }
    }

    @Override
    public List<StockInfo> fetchStockInfos() {
        List<FinMindDto.StockInfoRow> rows = call("TaiwanStockInfo", null, null, null,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.StockInfoRow>>() {
                });

        // 同一檔股票可能出現多筆（不同產業別），只保留第一筆
        Map<String, StockInfo> infos = new LinkedHashMap<>();
        int skipped = 0;
        for (FinMindDto.StockInfoRow row : rows) {
            if (row.stockId() == null || row.stockName() == null) {
                skipped++;
                continue;
            }
            // 第一版只做上市（twse）的股票與 ETF；代號是 4 碼數字開頭，例如 2330、0050、00878
            if (!"twse".equals(row.type()) || !row.stockId().matches(LISTED_STOCK_ID)) {
                skipped++;
                continue;
            }
            StockType type = "ETF".equals(row.industryCategory()) ? StockType.ETF : StockType.STOCK;
            infos.putIfAbsent(row.stockId(),
                    new StockInfo(row.stockId(), row.stockName(), type, row.industryCategory()));
        }
        log.info("[FinMind] 股票清單共 {} 檔上市股票與 ETF（略過上櫃、興櫃、指數等 {} 筆）", infos.size(), skipped);
        return List.copyOf(infos.values());
    }

    @Override
    public List<PriceBar> fetchPrices(String stockId, LocalDate from, LocalDate to) {
        List<FinMindDto.PriceRow> rows = call("TaiwanStockPrice", stockId, from, to,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.PriceRow>>() {
                });

        List<PriceBar> bars = new ArrayList<>();
        int skipped = 0;
        for (FinMindDto.PriceRow row : rows) {
            // 沒有成交的日子 FinMind 會回傳 0，不能拿來回測
            if (row.open() == null || row.close() == null || row.max() == null || row.min() == null
                    || row.open().signum() == 0 || row.close().signum() == 0) {
                skipped++;
                log.debug("[FinMind] 略過無成交資料 stockId={} date={}", stockId, row.date());
                continue;
            }
            bars.add(new PriceBar(stockId, LocalDate.parse(row.date()),
                    row.open(), row.max(), row.min(), row.close(),
                    row.tradingVolume() == null ? 0L : row.tradingVolume()));
        }
        if (skipped > 0) {
            log.info("[FinMind] {} 共略過 {} 筆無成交資料", stockId, skipped);
        }
        return bars;
    }

    /**
     * 美股清單。FinMind 的 USStockInfo 有一萬多筆，混了好幾個日期的快照（下市的也在裡面）、
     * 特別股（ABR^D）、權證、SPAC 等，所以：
     * <ol>
     *   <li>只取最新日期的快照（目前還在掛牌的）</li>
     *   <li>代號只收一般格式；BRK/B 轉成 BRK-B（查價格要用這個寫法）</li>
     *   <li>ETF 全收；公司要市值 10 億美元以上，而且名稱不是特別股、債券、權證</li>
     * </ol>
     */
    @Override
    public List<StockInfo> fetchUsStockInfos() {
        List<FinMindDto.UsStockInfoRow> rows = call("USStockInfo", null, null, null,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.UsStockInfoRow>>() {
                });

        String latest = rows.stream()
                .map(FinMindDto.UsStockInfoRow::date)
                .filter(date -> date != null && ISO_DATE.matcher(date).matches())
                .max(String::compareTo)
                .orElse(null);
        if (latest == null) {
            log.warn("[FinMind] 美股清單沒有任何有效日期，略過");
            return List.of();
        }

        Map<String, StockInfo> infos = new LinkedHashMap<>();
        int etfCount = 0;
        for (FinMindDto.UsStockInfoRow row : rows) {
            if (!latest.equals(row.date()) || row.stockId() == null || row.stockName() == null
                    || !US_TICKER.matcher(row.stockId()).matches()) {
                continue;
            }
            boolean etf = "ETF".equals(row.subsector());
            if (!etf && (row.marketCap() == null || row.marketCap().compareTo(US_MIN_MARKET_CAP) < 0
                    || US_NON_COMMON.matcher(row.stockName()).find())) {
                continue;
            }
            String stockId = row.stockId().replace('/', '-');
            String industry = row.subsector() == null || row.subsector().isBlank() || "n/a".equals(row.subsector())
                    ? null : truncate(row.subsector().strip());
            infos.putIfAbsent(stockId, new StockInfo(stockId, cleanUsName(row.stockName()),
                    etf ? StockType.ETF : StockType.STOCK, Market.US, industry));
            if (etf) {
                etfCount++;
            }
        }
        log.info("[FinMind] 美股清單（{} 快照）共 {} 檔：公司 {} 檔、ETF {} 檔（原始 {} 筆，略過舊快照、特別股、小型股等）",
                latest, infos.size(), infos.size() - etfCount, etfCount, rows.size());
        return List.copyOf(infos.values());
    }

    /** "Apple Inc. Common Stock" → "Apple Inc."；括號說明也拿掉；太長的截斷 */
    static String cleanUsName(String raw) {
        String name = raw.strip().replaceAll("\\s*\\(.*\\)$", "");
        name = US_NAME_SUFFIX.matcher(name).replaceAll("").strip();
        return truncate(name.isEmpty() ? raw.strip() : name);
    }

    private static String truncate(String text) {
        return text.length() <= NAME_MAX_LENGTH ? text : text.substring(0, NAME_MAX_LENGTH);
    }

    /**
     * 美股日線，換算成還原價：FinMind 的 Close 只還原了分割，Adj_Close 再還原股利。
     * 開高低也乘上同一個比例（Adj_Close / Close），K 線形狀才不會變。
     * 最新一天沒有之後的除息，比例是 1，所以畫面上的最新收盤就是真實價格。
     */
    @Override
    public List<PriceBar> fetchUsPrices(String stockId, LocalDate from, LocalDate to) {
        List<FinMindDto.UsPriceRow> rows = call("USStockPrice", stockId, from, to,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.UsPriceRow>>() {
                });

        List<PriceBar> bars = new ArrayList<>();
        int skipped = 0;
        for (FinMindDto.UsPriceRow row : rows) {
            if (row.date() == null || row.open() == null || row.high() == null || row.low() == null
                    || row.close() == null || row.adjClose() == null
                    || row.close().signum() <= 0 || row.adjClose().signum() <= 0 || row.open().signum() <= 0) {
                skipped++;
                continue;
            }
            BigDecimal ratio = row.adjClose().divide(row.close(), 10, RoundingMode.HALF_UP);
            bars.add(new PriceBar(stockId, LocalDate.parse(row.date()),
                    adjust(row.open(), ratio), adjust(row.high(), ratio), adjust(row.low(), ratio),
                    row.adjClose().setScale(2, RoundingMode.HALF_UP),
                    row.volume() == null ? 0L : row.volume().longValue()));
        }
        if (skipped > 0) {
            log.info("[FinMind] {} 共略過 {} 筆資料不完整的美股日線", stockId, skipped);
        }
        return bars;
    }

    private static BigDecimal adjust(BigDecimal price, BigDecimal ratio) {
        return price.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<CashDividend> fetchCashDividends(String stockId, LocalDate from, LocalDate to) {
        List<FinMindDto.DividendResultRow> rows = call("TaiwanStockDividendResult", stockId, from, to,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.DividendResultRow>>() {
                });

        List<CashDividend> dividends = new ArrayList<>();
        for (FinMindDto.DividendResultRow row : rows) {
            if ("息".equals(row.kind()) && row.value() != null && row.value().signum() > 0) {
                dividends.add(new CashDividend(stockId, LocalDate.parse(row.date()), row.value()));
            } else if ("權息".equals(row.kind())) {
                // 權息合併的數值拆不出現金部分，第一版先略過（已列為已知限制）
                log.warn("[FinMind] 略過權息合併資料 stockId={} date={} value={}", stockId, row.date(), row.value());
            } else {
                log.debug("[FinMind] 略過非現金股利 stockId={} date={} kind={}", stockId, row.date(), row.kind());
            }
        }
        return dividends;
    }

    /**
     * 股票分割和面額變更是兩個不同的 dataset，各呼叫一次再合併、依日期排序。
     * 兩者對回測的影響一樣：股數乘上「事件前價格 / 事件後價格」。
     *
     * 兩個 dataset 各自容錯：其中一個失敗只記 WARN，另一個的結果照樣回傳，
     * 不會因為面額變更抓不到，就連股票分割也一起丟掉。
     */
    @Override
    public List<ShareSplit> fetchShareSplits(String stockId, LocalDate from, LocalDate to) {
        List<ShareSplit> splits = new ArrayList<>();

        try {
            List<FinMindDto.SplitPriceRow> splitRows = call("TaiwanStockSplitPrice", stockId, from, to,
                    new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.SplitPriceRow>>() {
                    });
            for (FinMindDto.SplitPriceRow row : splitRows) {
                addIfValid(splits, stockId, row.date(), SplitType.SPLIT, row.beforePrice(), row.afterPrice());
            }
        } catch (FinMindApiException e) {
            log.warn("[FinMind] {} 股票分割資料抓取失敗，略過：{}", stockId, e.getMessage());
        }

        try {
            // 這個 dataset 不接受 data_id（帶了會回 400），只能抓整個市場這段期間的資料，再自己挑出這一檔。
            // 面額變更很少見，整個市場 5 年也只有幾十筆，資料量不大。
            List<FinMindDto.ParValueChangeRow> parRows = call("TaiwanStockParValueChange", null, from, to,
                    new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.ParValueChangeRow>>() {
                    });
            for (FinMindDto.ParValueChangeRow row : parRows) {
                if (stockId.equals(row.stockId())) {
                    addIfValid(splits, stockId, row.date(), SplitType.PAR_VALUE_CHANGE, row.beforeClose(), row.afterRefClose());
                }
            }
        } catch (FinMindApiException e) {
            log.warn("[FinMind] {} 面額變更資料抓取失敗，略過：{}", stockId, e.getMessage());
        }

        splits.sort(Comparator.comparing(ShareSplit::date));
        if (!splits.isEmpty()) {
            log.info("[FinMind] {} 共有 {} 筆股票分割或面額變更：{}", stockId, splits.size(),
                    splits.stream().map(s -> s.date() + " " + s.type()).toList());
        }
        return splits;
    }

    /** 整體市場三大法人買賣金額（FinMind 的 TaiwanStockTotalInstitutionalInvestors），資料不完整的略過 */
    @Override
    public List<InstitutionalTrade> fetchInstitutionalTrades(LocalDate from, LocalDate to) {
        List<FinMindDto.InstitutionalRow> rows = call("TaiwanStockTotalInstitutionalInvestors", null, from, to,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.InstitutionalRow>>() {
                });
        List<InstitutionalTrade> trades = new ArrayList<>();
        for (FinMindDto.InstitutionalRow row : rows) {
            if (row.date() == null || row.name() == null || row.buy() == null || row.sell() == null) {
                continue;
            }
            trades.add(new InstitutionalTrade(LocalDate.parse(row.date()), row.name(), row.buy(), row.sell()));
        }
        return trades;
    }

    /**
     * 匯率（臺灣銀行牌告，FinMind 的 TaiwanExchangeRate）。
     * 沒有報價的日子買入、賣出會是 -99，這種資料略過。
     */
    @Override
    public List<ExchangeRate> fetchExchangeRates(String currency, LocalDate from, LocalDate to) {
        List<FinMindDto.ExchangeRateRow> rows = call("TaiwanExchangeRate", currency, from, to,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.ExchangeRateRow>>() {
                });
        List<ExchangeRate> rates = new ArrayList<>();
        for (FinMindDto.ExchangeRateRow row : rows) {
            if (row.date() == null || row.spotBuy() == null || row.spotSell() == null
                    || row.spotBuy().signum() <= 0 || row.spotSell().signum() <= 0) {
                continue;
            }
            rates.add(new ExchangeRate(currency, LocalDate.parse(row.date()), row.spotBuy(), row.spotSell()));
        }
        rates.sort(Comparator.comparing(ExchangeRate::date));
        return rates;
    }

    /**
     * 相關新聞。FinMind 這個 dataset 資料量大，一次只給一天，所以只帶 start_date。
     * 標題或連結是空的、連結不是 http(s) 開頭的都略過：連結會直接放進網頁的 href，
     * 不檢查的話 "javascript:..." 這種連結點下去就會執行程式碼（XSS）。
     */
    @Override
    public List<NewsItem> fetchNews(String stockId, LocalDate date) {
        List<FinMindDto.NewsRow> rows = call("TaiwanStockNews", stockId, date, null,
                new ParameterizedTypeReference<FinMindDto.Response<FinMindDto.NewsRow>>() {
                });

        List<NewsItem> news = new ArrayList<>();
        int skipped = 0;
        for (FinMindDto.NewsRow row : rows) {
            LocalDateTime publishedAt = parseNewsTime(row.date());
            if (row.title() == null || row.title().isBlank() || !isWebLink(row.link()) || publishedAt == null) {
                skipped++;
                continue;
            }
            news.add(new NewsItem(stockId, publishedAt, row.title().strip(),
                    row.source() == null ? "" : row.source().strip(), row.link().strip()));
        }
        if (skipped > 0) {
            log.info("[FinMind] {} {} 新聞略過 {} 筆資料不完整或連結不安全", stockId, date, skipped);
        }
        return news;
    }

    private static final DateTimeFormatter NEWS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** FinMind 的新聞時間是 "2026-11-03 14:05:12"，偶爾只有日期；都解析不了回 null */
    static LocalDateTime parseNewsTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        try {
            return LocalDateTime.parse(text, NEWS_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    static boolean isWebLink(String link) {
        if (link == null) {
            return false;
        }
        String lower = link.strip().toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private void addIfValid(List<ShareSplit> splits, String stockId, String date, SplitType type,
                            BigDecimal before, BigDecimal after) {
        if (date == null || before == null || after == null || before.signum() <= 0 || after.signum() <= 0) {
            log.warn("[FinMind] 略過資料不完整的{} stockId={} date={} before={} after={}",
                    type == SplitType.SPLIT ? "股票分割" : "面額變更", stockId, date, before, after);
            return;
        }
        splits.add(new ShareSplit(stockId, LocalDate.parse(date), type, before, after));
    }

    /** FinMind 的錯誤回應會帶 token 尾碼（token_tail），寫進 log 前先遮掉 */
    static String maskToken(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        return responseBody.replaceAll("\"token_tail\"\\s*:\\s*\"[^\"]*\"", "\"token_tail\":\"***\"");
    }

    /**
     * 呼叫 FinMind 的共用方法：組參數、帶 token、記錄耗時、檢查狀態。
     * 注意：log 不可以印出 token。
     */
    private <T> List<T> call(String dataset, String dataId, LocalDate from, LocalDate to,
                             ParameterizedTypeReference<FinMindDto.Response<T>> responseType) {
        log.info("[FinMind] 呼叫 dataset={} dataId={} 期間 {} ~ {}", dataset, dataId, from, to);
        long start = System.currentTimeMillis();

        FinMindDto.Response<T> body;
        try {
            body = restClient.get()
                    .uri(builder -> {
                        builder.queryParam("dataset", dataset);
                        if (dataId != null) {
                            builder.queryParam("data_id", dataId);
                        }
                        if (from != null) {
                            builder.queryParam("start_date", from);
                        }
                        if (to != null) {
                            builder.queryParam("end_date", to);
                        }
                        return builder.build();
                    })
                    .headers(headers -> {
                        if (properties.hasToken()) {
                            headers.setBearerAuth(properties.token());
                        }
                    })
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            log.error("[FinMind] 呼叫失敗 dataset={} dataId={} HTTP {} 回應：{}",
                    dataset, dataId, e.getStatusCode().value(), maskToken(e.getResponseBodyAsString()));
            throw new FinMindApiException("FinMind 呼叫失敗：HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            log.error("[FinMind] 無法連線 dataset={} dataId={}：{}", dataset, dataId, e.getMessage());
            throw new FinMindApiException("無法連線 FinMind：" + e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        if (body == null || body.status() == null || body.status() != 200) {
            String msg = body == null ? "沒有回應內容" : body.msg();
            log.error("[FinMind] 回應狀態異常 dataset={} dataId={} status={} msg={}",
                    dataset, dataId, body == null ? null : body.status(), msg);
            throw new FinMindApiException("FinMind 回應異常：" + msg);
        }

        List<T> data = body.data() == null ? List.of() : body.data();
        log.info("[FinMind] 完成 dataset={} dataId={} 共 {} 筆，耗時 {} ms", dataset, dataId, data.size(), elapsed);
        return data;
    }
}
