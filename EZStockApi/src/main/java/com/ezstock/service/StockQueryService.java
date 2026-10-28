package com.ezstock.service;

import com.ezstock.dto.PricePoint;
import com.ezstock.dto.StockDetail;
import com.ezstock.dto.StockSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票查詢：搜尋、個股資料、歷史日線。
 */
public interface StockQueryService {

    /** 依代號或名稱搜尋，最多回傳 20 筆 */
    List<StockSummary> search(String keyword);

    /** 個股基本資料與最新行情；查無股票丟出 ResourceNotFoundException */
    StockDetail getDetail(String stockId);

    /**
     * 某段期間的日線，依日期由舊到新。
     * from、to 可以不給：to 預設今天，from 預設 to 往前一年。
     */
    List<PricePoint> getPrices(String stockId, LocalDate from, LocalDate to);
}
