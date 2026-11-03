package com.ezstock.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一則新聞。同一則新聞可能同時是好幾檔股票的相關新聞（例如同時提到台積電和鴻海），
 * 合併成一筆，stocks 列出所有相關的股票。
 */
public record NewsResponse(String title, String source, String link, LocalDateTime publishedAt,
                           List<RelatedStock> stocks) {

    public record RelatedStock(String stockId, String name) {
    }
}
