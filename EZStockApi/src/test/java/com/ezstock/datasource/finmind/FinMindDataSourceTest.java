package com.ezstock.datasource.finmind;

import com.ezstock.datasource.CashDividend;
import com.ezstock.datasource.PriceBar;
import com.ezstock.datasource.ShareSplit;
import com.ezstock.datasource.StockInfo;
import com.ezstock.model.SplitType;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 不連真的 FinMind，用 MockRestServiceServer 模擬回應，
 * 測試參數有沒有帶對、JSON 有沒有正確轉換。
 */
class FinMindDataSourceTest {

    private static final String URL = "https://finmind.test/api/v4/data";

    private MockRestServiceServer server;
    private FinMindDataSource dataSource;

    private void setUp(String token) {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        dataSource = new FinMindDataSource(builder.build(), new FinMindProperties(URL, token));
    }

    @Test
    @DisplayName("日線：帶正確參數與 token，並略過沒有成交的日子")
    void fetchPrices() {
        setUp("my-token");
        server.expect(queryParam("dataset", "TaiwanStockPrice"))
                .andExpect(queryParam("data_id", "2330"))
                .andExpect(queryParam("start_date", "2026-01-01"))
                .andExpect(queryParam("end_date", "2026-01-31"))
                .andExpect(header("Authorization", "Bearer my-token"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2026-01-02","stock_id":"2330","Trading_Volume":1000,"Trading_money":105500,
                           "open":100.0,"max":106.0,"min":99.5,"close":105.5,"spread":5.5,"Trading_turnover":10},
                          {"date":"2026-01-05","stock_id":"2330","Trading_Volume":0,"Trading_money":0,
                           "open":0,"max":0,"min":0,"close":0,"spread":0,"Trading_turnover":0}
                        ]}""", MediaType.APPLICATION_JSON));

        List<PriceBar> bars = dataSource.fetchPrices("2330",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        server.verify();
        assertThat(bars).hasSize(1);
        PriceBar bar = bars.get(0);
        assertThat(bar.date()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(bar.high()).isEqualByComparingTo("106.0");
        assertThat(bar.low()).isEqualByComparingTo("99.5");
        assertThat(bar.close()).isEqualByComparingTo("105.5");
        assertThat(bar.volume()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("股利：只保留現金股利（息），略過配股與權息合併")
    void fetchCashDividends() {
        setUp(null);
        server.expect(queryParam("dataset", "TaiwanStockDividendResult"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2025-03-18","stock_id":"2330","stock_and_cache_dividend":4.5,"stock_or_cache_dividend":"息"},
                          {"date":"2025-06-18","stock_id":"2330","stock_and_cache_dividend":1.0,"stock_or_cache_dividend":"權"},
                          {"date":"2025-09-18","stock_id":"2330","stock_and_cache_dividend":3.0,"stock_or_cache_dividend":"權息"}
                        ]}""", MediaType.APPLICATION_JSON));

        List<CashDividend> dividends = dataSource.fetchCashDividends("2330",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        server.verify();
        assertThat(dividends).singleElement().satisfies(d -> {
            assertThat(d.exDate()).isEqualTo(LocalDate.of(2025, 3, 18));
            assertThat(d.cashDividend()).isEqualByComparingTo("4.5");
        });
    }

    @Test
    @DisplayName("股票分割與面額變更：兩個 dataset 合併、依日期排序，略過價格不完整的")
    void fetchShareSplits() {
        setUp("my-token");
        server.expect(queryParam("dataset", "TaiwanStockSplitPrice"))
                .andExpect(queryParam("data_id", "0050"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2025-06-18","stock_id":"0050","type":"分割","before_price":188.65,"after_price":47.16,
                           "max_price":51.85,"min_price":42.45,"open_price":47.16},
                          {"date":"2025-07-01","stock_id":"0050","type":"分割","before_price":null,"after_price":10}
                        ]}""", MediaType.APPLICATION_JSON));
        // 面額變更不能帶 data_id，會回傳整個市場的資料，要自己挑出 0050
        server.expect(queryParam("dataset", "TaiwanStockParValueChange"))
                .andExpect(request -> assertThat(request.getURI().getQuery()).doesNotContain("data_id"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2024-03-04","stock_id":"0050","stock_name":"元大台灣50","before_close":100.0,
                           "after_ref_close":10.0,"after_ref_max":11.0,"after_ref_min":9.0,"after_ref_open":10.0},
                          {"date":"2024-05-06","stock_id":"1234","stock_name":"別檔","before_close":50.0,
                           "after_ref_close":5.0,"after_ref_max":5.5,"after_ref_min":4.5,"after_ref_open":5.0}
                        ]}""", MediaType.APPLICATION_JSON));

        List<ShareSplit> splits = dataSource.fetchShareSplits("0050",
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));

        server.verify();
        assertThat(splits).extracting(ShareSplit::date)
                .containsExactly(LocalDate.of(2024, 3, 4), LocalDate.of(2025, 6, 18));
        assertThat(splits.get(0).type()).isEqualTo(SplitType.PAR_VALUE_CHANGE);
        assertThat(splits.get(1).type()).isEqualTo(SplitType.SPLIT);
        assertThat(splits.get(1).beforePrice()).isEqualByComparingTo("188.65");
    }

    @Test
    @DisplayName("面額變更抓取失敗時，股票分割的結果照樣回傳")
    void shareSplitsWhenParValueFails() {
        setUp("my-token");
        server.expect(queryParam("dataset", "TaiwanStockSplitPrice"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2025-06-18","stock_id":"0050","type":"分割","before_price":188.65,"after_price":47.16}
                        ]}""", MediaType.APPLICATION_JSON));
        server.expect(queryParam("dataset", "TaiwanStockParValueChange"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"parameter data_id don't provide\",\"status\":400}"));

        List<ShareSplit> splits = dataSource.fetchShareSplits("0050",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        server.verify();
        assertThat(splits).singleElement().extracting(ShareSplit::date).isEqualTo(LocalDate.of(2025, 6, 18));
    }

    @Test
    @DisplayName("股票清單：只留上市股票與 ETF，同一檔只留一筆，ETF 依產業別判斷")
    void fetchStockInfos() {
        setUp("my-token");
        server.expect(queryParam("dataset", "TaiwanStockInfo"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"industry_category":"ETF","stock_id":"0050","stock_name":"元大台灣50","type":"twse","date":"2026-10-27"},
                          {"industry_category":"半導體業","stock_id":"2330","stock_name":"台積電","type":"twse","date":"2026-10-27"},
                          {"industry_category":"電子工業","stock_id":"2330","stock_name":"台積電","type":"twse","date":"2026-10-27"},
                          {"industry_category":"ETF","stock_id":"00679B","stock_name":"元大美債20年","type":"twse","date":"2026-10-27"},
                          {"industry_category":"半導體業","stock_id":"5483","stock_name":"中美晶","type":"tpex","date":"2026-10-27"},
                          {"industry_category":"Index","stock_id":"TAIEX","stock_name":"發行量加權股價指數","type":"twse","date":"2026-10-27"}
                        ]}""", MediaType.APPLICATION_JSON));

        List<StockInfo> infos = dataSource.fetchStockInfos();

        server.verify();
        assertThat(infos).extracting(StockInfo::stockId).containsExactly("0050", "2330", "00679B");
        assertThat(infos.get(0).type()).isEqualTo(StockType.ETF);
        assertThat(infos.get(1).type()).isEqualTo(StockType.STOCK);
        assertThat(infos.get(1).industry()).isEqualTo("半導體業");
    }

    @Test
    @DisplayName("token：沒填、佔位文字、含非英數字元都當作沒有 token")
    void hasToken() {
        assertThat(new FinMindProperties(URL, null).hasToken()).isFalse();
        assertThat(new FinMindProperties(URL, "").hasToken()).isFalse();
        assertThat(new FinMindProperties(URL, "YOUR_FINMIND_TOKEN").hasToken()).isFalse();
        assertThat(new FinMindProperties(URL, "之後填入").hasToken()).isFalse();
        assertThat(new FinMindProperties(URL, "eyJhbGciOi.eyJ1c2VyX2lk.abc-123_XYZ").hasToken()).isTrue();
    }

    @Test
    @DisplayName("錯誤回應寫進 log 前，會遮掉 token 尾碼")
    void maskToken() {
        String body = "{\"msg\":\"Token is illegal.\",\"status\":400,\"token_tail\":\"...abc123\"}";

        assertThat(FinMindDataSource.maskToken(body))
                .doesNotContain("abc123")
                .contains("\"token_tail\":\"***\"");
    }

    @Test
    @DisplayName("超過用量（HTTP 402）時丟出 FinMindApiException")
    void overLimit() {
        setUp("my-token");
        server.expect(queryParam("dataset", "TaiwanStockPrice"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"Requests reach the upper limit.\",\"status\":402}"));

        assertThatThrownBy(() -> dataSource.fetchPrices("2330",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .isInstanceOf(FinMindApiException.class)
                .hasMessageContaining("402");
    }
}
