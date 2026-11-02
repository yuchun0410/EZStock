package com.ezstock.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章內頁。
 *
 * @param editable 目前登入的人能不能修改、刪除（是不是作者）
 */
public record PostDetailResponse(Long id, String title, String content, AuthorResponse author,
                                 List<StockTag> stocks, int commentCount,
                                 LocalDateTime createdAt, LocalDateTime updatedAt, boolean edited,
                                 boolean editable, List<CommentResponse> comments) {
}
