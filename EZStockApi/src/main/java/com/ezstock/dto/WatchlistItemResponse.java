package com.ezstock.dto;

import com.ezstock.model.StockType;
import com.ezstock.model.WatchlistItem;

import java.time.LocalDateTime;

public record WatchlistItemResponse(String stockId, String name, StockType type, LocalDateTime addedAt) {

    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(item.getStock().getStockId(), item.getStock().getName(),
                item.getStock().getType(), item.getAddedAt());
    }
}
