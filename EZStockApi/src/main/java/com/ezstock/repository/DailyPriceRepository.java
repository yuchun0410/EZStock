package com.ezstock.repository;

import com.ezstock.model.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    // 某檔股票某段期間的日線，依日期由舊到新（回測、走勢圖用）
    List<DailyPrice> findByStock_StockIdAndTradeDateBetweenOrderByTradeDateAsc(
            String stockId, LocalDate from, LocalDate to);

    // 某檔股票最新一筆日線（個股頁顯示最新收盤用）
    Optional<DailyPrice> findFirstByStock_StockIdOrderByTradeDateDesc(String stockId);

    // 有價格資料的股票代號，每日更新和條件選股用
    @Query("SELECT DISTINCT p.stock.stockId FROM DailyPrice p ORDER BY p.stock.stockId")
    List<String> findStockIdsWithPrices();

    // 只查日期，給匯入時判斷哪幾天已經存在
    @Query("""
            SELECT p.tradeDate FROM DailyPrice p
            WHERE p.stock.stockId = :stockId AND p.tradeDate BETWEEN :from AND :to
            """)
    List<LocalDate> findTradeDates(@Param("stockId") String stockId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
