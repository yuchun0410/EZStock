package com.ezstock.repository;

import com.ezstock.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, WatchlistItem.Key> {

    /** JOIN FETCH：一次把股票名稱一起查出來，避免每一檔再各查一次（N+1 問題） */
    @Query("SELECT w FROM WatchlistItem w JOIN FETCH w.stock WHERE w.id.memberId = :memberId ORDER BY w.addedAt")
    List<WatchlistItem> findByMember(@Param("memberId") Long memberId);
}
