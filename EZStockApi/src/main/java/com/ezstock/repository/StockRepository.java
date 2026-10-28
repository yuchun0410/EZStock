package com.ezstock.repository;

import com.ezstock.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, String> {

    // 用代號或名稱模糊搜尋，例如 "2330" 或 "台積"
    List<Stock> findByStockIdContainingOrNameContainingOrderByStockIdAsc(String stockId, String name);
}
