package com.ezstock.service;

/**
 * 從外部資料來源匯入股票清單、日線與股利。
 */
public interface ImportService {

    /**
     * 匯入全部：
     * 1. 股票清單（全部上市股票與 ETF）
     * 2. 設定檔指定股票的日線與股利（只新增還沒有的資料）
     */
    ImportResult importAll();

    /** 只匯入一檔的日線與股利（股票必須已在股票清單中） */
    StockImportResult importStock(String stockId);
}
