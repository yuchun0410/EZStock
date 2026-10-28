package com.ezstock.service;

import java.util.List;

/**
 * 一次匯入的總結果。
 *
 * @param stockListCount 存入的股票清單檔數
 * @param succeeded      價格與股利匯入成功的股票
 * @param failed         匯入失敗的股票代號
 * @param elapsedMillis  總耗時（毫秒）
 */
public record ImportResult(int stockListCount,
                           List<StockImportResult> succeeded,
                           List<String> failed,
                           long elapsedMillis) {

    public int totalPricesInserted() {
        return succeeded.stream().mapToInt(StockImportResult::pricesInserted).sum();
    }

    public int totalDividendsInserted() {
        return succeeded.stream().mapToInt(StockImportResult::dividendsInserted).sum();
    }

    public int totalSplitsInserted() {
        return succeeded.stream().mapToInt(StockImportResult::splitsInserted).sum();
    }
}
