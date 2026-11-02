package com.ezstock.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表的一篇。
 *
 * @param excerpt 內文開頭的一小段（換行與連續空白會合併成一個空格）
 * @param edited  建立後有沒有修改過
 */
public record PostSummaryResponse(Long id, String title, String excerpt, AuthorResponse author,
                                  List<StockTag> stocks, int commentCount,
                                  LocalDateTime createdAt, boolean edited) {
}
