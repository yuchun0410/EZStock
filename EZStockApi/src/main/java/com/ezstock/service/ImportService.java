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

    /**
     * 補上一檔股票最新的資料：從資料庫最新一筆的隔天抓到今天。
     * 已經是最新的就不呼叫外部 API；資料庫完全沒有這檔的價格時，等同 importStock。
     */
    StockImportResult updateStock(String stockId);

    /** 每日更新：資料庫裡所有有價格的股票，各自補上最新資料。某一檔失敗不影響其他檔 */
    ImportResult updateAll();
}
