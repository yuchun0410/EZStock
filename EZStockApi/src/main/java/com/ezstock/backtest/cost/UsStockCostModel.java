package com.ezstock.backtest.cost;

import com.ezstock.model.StockType;

import java.math.BigDecimal;

/**
 * 美股的交易成本：多數美國券商（Firstrade、Schwab 等）買賣美股、ETF 都是零手續費，
 * 也沒有台股那樣的證交稅；賣出時的 SEC 規費、TAF 是成交金額的百萬分之幾，金額太小不計。
 * 用台灣券商複委託的話手續費會高很多，畫面上的說明會寫清楚。
 */
public class UsStockCostModel implements CostModel {

    @Override
    public BigDecimal buyCost(BigDecimal amount, StockType type) {
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal sellCost(BigDecimal amount, StockType type) {
        return BigDecimal.ZERO;
    }

    @Override
    public String describe() {
        return "美股以美國券商零手續費計算，賣出規費金額極小不計（用台灣券商複委託的話成本會更高）";
    }
}
