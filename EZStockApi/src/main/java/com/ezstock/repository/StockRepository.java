package com.ezstock.repository;

import com.ezstock.model.Stock;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, String> {

    /**
     * 用代號或名稱模糊搜尋，例如 "2330"、"台積"、"AAPL"、"apple"。
     * 代號完全相符的排第一：加入美股後，搜尋 "T" 會有上百檔代號或名稱含 T，但使用者要找的是 T（AT&T）本身。
     * 多帶一個 Limit 參數，Spring Data 產生的 SQL 就會加上 LIMIT。
     */
    @Query("""
            SELECT s FROM Stock s
            WHERE s.stockId LIKE CONCAT('%', :keyword, '%') OR s.name LIKE CONCAT('%', :keyword, '%')
            ORDER BY CASE WHEN s.stockId = :keyword THEN 0 ELSE 1 END, s.stockId
            """)
    List<Stock> search(@Param("keyword") String keyword, Limit limit);
}
