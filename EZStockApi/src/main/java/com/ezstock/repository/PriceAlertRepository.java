package com.ezstock.repository;

import com.ezstock.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    @Query("SELECT a FROM PriceAlert a JOIN FETCH a.stock "
            + "WHERE a.memberId = :memberId AND a.triggeredAt IS NULL ORDER BY a.id")
    List<PriceAlert> findActiveByMember(@Param("memberId") Long memberId);

    long deleteByIdAndMemberId(Long id, Long memberId);
}
