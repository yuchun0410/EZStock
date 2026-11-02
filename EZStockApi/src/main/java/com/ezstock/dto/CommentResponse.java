package com.ezstock.dto;

import java.time.LocalDateTime;

/**
 * 一則留言。被刪除的留言 author、content 都是 null，畫面顯示「此留言已刪除」。
 *
 * @param deletable 目前登入的人能不能刪除（是不是自己的留言）
 */
public record CommentResponse(Long id, AuthorResponse author, String content, boolean deleted,
                              LocalDateTime createdAt, boolean deletable) {
}
