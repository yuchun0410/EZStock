package com.ezstock.dao;

import com.ezstock.model.StockSplit;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票分割、面額變更的存取介面。
 */
public interface StockSplitDao {

    /** 某檔股票某段期間的分割事件，依日期由舊到新 */
    List<StockSplit> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to);

    /** 批次新增，回傳新增筆數 */
    int insertAll(List<StockSplit> splits);
}
