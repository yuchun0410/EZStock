package com.ezstock.service;

/**
 * 確保某檔股票有價格資料：沒有的話，當下從外部資料來源載入。
 */
public interface PriceLoader {

    /**
     * 資料庫已經有這檔的價格就直接返回；沒有就去外部資料來源抓回來存起來。
     * 外部資料來源失敗時，例外會往外丟。
     */
    void ensureLoaded(String stockId);
}
