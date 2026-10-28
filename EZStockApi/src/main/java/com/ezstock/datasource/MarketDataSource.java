package com.ezstock.datasource;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情資料來源。
 * 匯入流程只依賴這個介面，之後要換成證交所、CSV 等來源，新增實作即可。
 */
public interface MarketDataSource {

    /** 股票清單（代號、名稱、類型、產業） */
    List<StockInfo> fetchStockInfos();

    /** 某檔股票某段期間的日線 */
    List<PriceBar> fetchPrices(String stockId, LocalDate from, LocalDate to);

    /** 某檔股票某段期間的現金股利 */
    List<CashDividend> fetchCashDividends(String stockId, LocalDate from, LocalDate to);
}
