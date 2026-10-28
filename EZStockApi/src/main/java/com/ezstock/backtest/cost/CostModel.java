package com.ezstock.backtest.cost;

import com.ezstock.model.StockType;

import java.math.BigDecimal;

/**
 * 交易成本的計算方式。
 *
 * 回測引擎只認得這個介面，不知道背後怎麼算。
 * 要換成「不計成本」或「券商打 6 折」，只要換一個實作傳進去，回測程式完全不用改（多型）。
 */
public interface CostModel {

    /**
     * 買進要付的成本（不含股票本身的價金）。
     *
     * @param amount 成交金額 = 成交價 × 股數
     */
    BigDecimal buyCost(BigDecimal amount, StockType type);

    /**
     * 賣出要付的成本（手續費 + 證交稅）。
     *
     * @param amount 成交金額 = 成交價 × 股數
     */
    BigDecimal sellCost(BigDecimal amount, StockType type);

    /** 給畫面和 log 看的說明，例如「手續費 0.1425% × 6 折，最低 20 元；證交稅 股票 0.3%、ETF 0.1%」 */
    String describe();
}
