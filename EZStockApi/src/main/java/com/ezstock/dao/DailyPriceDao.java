package com.ezstock.dao;

import com.ezstock.model.DailyPrice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 每日股價的存取介面。
 */
public interface DailyPriceDao {

    /** 某檔股票某段期間的日線，依日期由舊到新 */
    List<DailyPrice> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to);

    /** 某檔股票最新一筆日線 */
    Optional<DailyPrice> findLatest(String stockId);

    /** 資料庫裡有價格資料的股票代號（每日更新、條件選股的範圍） */
    List<String> findStockIdsWithPrices();

    /** 某段期間已存在的交易日，匯入前用來過濾重複資料 */
    List<LocalDate> findTradeDates(String stockId, LocalDate from, LocalDate to);

    /** 批次新增，回傳新增筆數 */
    int insertAll(List<DailyPrice> prices);
}
