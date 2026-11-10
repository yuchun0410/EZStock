package com.ezstock.datasource;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情資料來源。
 * 匯入流程只依賴這個介面，之後要換成證交所、CSV 等來源，新增實作即可。
 */
public interface MarketDataSource {

    /** 台股清單（代號、名稱、類型、產業） */
    List<StockInfo> fetchStockInfos();

    /** 某檔台股某段期間的日線（原始價格，除權息另外用股利、分割資料處理） */
    List<PriceBar> fetchPrices(String stockId, LocalDate from, LocalDate to);

    /** 美股清單：只挑一般人會查的（市值夠大的公司 + ETF），排除特別股、權證、SPAC 單位 */
    List<StockInfo> fetchUsStockInfos();

    /**
     * 某檔美股某段期間的日線，已還原股利與分割（還原價）。
     * 外部資料來源沒有美股的股利、分割資料，所以美股的回測直接用還原價，等於股利再投入。
     */
    List<PriceBar> fetchUsPrices(String stockId, LocalDate from, LocalDate to);

    /** 某檔股票某段期間的現金股利 */
    List<CashDividend> fetchCashDividends(String stockId, LocalDate from, LocalDate to);

    /** 某檔股票某段期間的股票分割與面額變更（會讓股數改變的事件） */
    List<ShareSplit> fetchShareSplits(String stockId, LocalDate from, LocalDate to);

    /** 某種外幣某段期間對台幣的匯率（例如 USD），依日期由舊到新 */
    List<ExchangeRate> fetchExchangeRates(String currency, LocalDate from, LocalDate to);

    /** 某檔股票某一天的相關新聞 */
    List<NewsItem> fetchNews(String stockId, LocalDate date);
}
