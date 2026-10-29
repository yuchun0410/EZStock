package com.ezstock.dao;

import com.ezstock.model.WatchlistItem;

import java.util.List;

/**
 * 會員自選股的存取介面。
 */
public interface WatchlistDao {

    /** 某個會員的自選股（含股票名稱），依加入時間由舊到新 */
    List<WatchlistItem> findByMember(long memberId);

    /** 新增；已經有了就不動。回傳是否真的新增 */
    boolean add(WatchlistItem item);

    /** 移除；回傳是否真的有刪到 */
    boolean remove(long memberId, String stockId);
}
