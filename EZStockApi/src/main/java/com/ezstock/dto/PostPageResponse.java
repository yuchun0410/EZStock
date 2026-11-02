package com.ezstock.dto;

import java.util.List;

/**
 * 文章列表的一頁。
 *
 * @param page       第幾頁，從 0 開始
 * @param totalPages 總頁數
 */
public record PostPageResponse(List<PostSummaryResponse> items, int page, int size, long total, int totalPages) {
}
