package com.ezstock.dao;

import com.ezstock.model.Dividend;

import java.time.LocalDate;
import java.util.List;

/**
 * 除權息資料的存取介面。
 */
public interface DividendDao {

    /** 某檔股票某段期間的除息紀錄，依除息日由舊到新 */
    List<Dividend> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to);

    /** 批次新增，回傳新增筆數 */
    int insertAll(List<Dividend> dividends);
}
