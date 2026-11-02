package com.ezstock.dao;

import com.ezstock.model.StockHolding;

import java.util.List;

/**
 * 股票持股的存取介面。
 */
public interface StockHoldingDao {

    /** 某個會員的持股（含股票名稱），依股票代號排序 */
    List<StockHolding> findByMember(long memberId);

    /** 新增或更新（同一檔只會有一筆） */
    void upsert(StockHolding holding);

    boolean delete(long memberId, String stockId);
}
