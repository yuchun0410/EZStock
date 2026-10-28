package com.ezstock.service;

/** 單一檔股票的匯入結果 */
public record StockImportResult(String stockId,
                                int pricesInserted, int pricesSkipped,
                                int dividendsInserted, int dividendsSkipped) {
}
