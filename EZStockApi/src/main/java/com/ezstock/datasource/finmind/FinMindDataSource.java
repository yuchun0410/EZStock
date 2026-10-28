package com.ezstock.datasource.finmind;

import com.ezstock.datasource.CashDividend;
import com.ezstock.datasource.MarketDataSource;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.StockInfo;
import com.ezstock.model.StockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 透過 FinMind API 取得台股資料。
 * 文件：https://finmind.github.io/quickstart/
 */
@Component
public class FinMindDataSource implements MarketDataSource {

    private static final Logger log = LoggerFactory.getLogger(FinMindDataSource.class);

    /** 上市股票與 ETF 的代號格式：4 碼數字開頭，後面最多 2 碼數字或英文 */
    private static final String LISTED_STOCK_ID = "\\d{4}[0-9A-Z]{0,2}";

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
