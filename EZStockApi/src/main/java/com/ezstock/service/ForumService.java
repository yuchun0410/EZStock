package com.ezstock.service;

import com.ezstock.dto.CommentRequest;
import com.ezstock.dto.PostDetailResponse;
import com.ezstock.dto.PostPageResponse;
import com.ezstock.dto.PostRequest;

/**
 * 論壇。viewerId 是目前登入的人（沒登入是 null），用來決定能不能修改、刪除。
 */
public interface ForumService {

    int PAGE_SIZE = 20;
    int MAX_STOCK_TAGS = 5;

    /** @param stockId 只看標記了這檔股票的文章；null 代表全部 */
    PostPageResponse listPosts(String stockId, int page);

    PostDetailResponse getPost(long postId, Long viewerId);

    PostDetailResponse createPost(long memberId, PostRequest request);

    PostDetailResponse updatePost(long memberId, long postId, PostRequest request);

    void deletePost(long memberId, long postId);

    /** 留言後回傳最新的文章內頁（含所有留言） */
    PostDetailResponse addComment(long memberId, long postId, CommentRequest request);

    PostDetailResponse deleteComment(long memberId, long commentId);
}
