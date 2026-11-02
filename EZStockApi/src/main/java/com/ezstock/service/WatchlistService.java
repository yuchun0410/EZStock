package com.ezstock.service;

import com.ezstock.dto.WatchlistItemResponse;

import java.util.List;

public interface WatchlistService {

    int MAX_ITEMS = 50;

    List<WatchlistItemResponse> list(long memberId);

    /** 加入自選股（重複加入不會出錯），回傳加入後的完整清單 */
    List<WatchlistItemResponse> add(long memberId, String stockId);

    /** 移除自選股（本來就沒有也不會出錯），回傳移除後的完整清單 */
    List<WatchlistItemResponse> remove(long memberId, String stockId);

    /**
     * 匯入瀏覽器裡的自選股：已經有的略過、查無的股票略過、超過上限的略過，不會因為某一檔失敗而整批失敗。
     */
    List<WatchlistItemResponse> importItems(long memberId, List<String> stockIds);
}
