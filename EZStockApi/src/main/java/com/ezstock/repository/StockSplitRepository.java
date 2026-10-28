package com.ezstock.repository;

import com.ezstock.model.StockSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StockSplitRepository extends JpaRepository<StockSplit, Long> {

    List<StockSplit> findByStock_StockIdAndSplitDateBetweenOrderBySplitDateAsc(
            String stockId, LocalDate from, LocalDate to);
}
