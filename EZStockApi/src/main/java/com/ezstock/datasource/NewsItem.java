package com.ezstock.datasource;

import java.time.LocalDateTime;

/**
 * 一則個股相關新聞。只存標題、來源、時間和原文連結，不存內文（內文版權屬於新聞來源，點連結看原文）。
 */
public record NewsItem(String stockId, LocalDateTime publishedAt, String title, String source, String link) {
}
