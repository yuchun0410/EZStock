package com.ezstock.repository;

import com.ezstock.model.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    // 某檔股票某段期間的除息紀錄（回測時把股利加回現金）
    List<Dividend> findByStock_StockIdAndExDateBetweenOrderByExDateAsc(
            String stockId, LocalDate from, LocalDate to);
}
