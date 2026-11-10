package com.ezstock.datasource;

import com.ezstock.model.Market;
import com.ezstock.model.StockType;

/** 外部來源的股票基本資料，與資料來源格式無關 */
public record StockInfo(String stockId, String name, StockType type, Market market, String industry) {

    /** 台股 */
    public StockInfo(String stockId, String name, StockType type, String industry) {
        this(stockId, name, type, Market.TW, industry);
    }
}
