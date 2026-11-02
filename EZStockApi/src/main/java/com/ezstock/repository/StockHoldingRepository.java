package com.ezstock.repository;

import com.ezstock.model.StockHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockHoldingRepository extends JpaRepository<StockHolding, StockHolding.Key> {

    /** JOIN FETCH 一次把股票名稱查出來，避免 N+1 */
    @Query("SELECT h FROM StockHolding h JOIN FETCH h.stock WHERE h.id.memberId = :memberId ORDER BY h.id.stockId")
    List<StockHolding> findByMember(@Param("memberId") Long memberId);
}
