package com.ezstock.dto;

import com.ezstock.model.Market;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;

/** 股票搜尋結果的一筆 */
public record StockSummary(String stockId, String name, StockType type, Market market, String industry) {

    public static StockSummary from(Stock stock) {
        return new StockSummary(stock.getStockId(), stock.getName(), stock.getType(), stock.getMarket(),
                stock.getIndustry());
    }
}
