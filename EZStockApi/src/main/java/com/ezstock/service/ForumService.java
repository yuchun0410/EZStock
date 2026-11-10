package com.ezstock.service;

import com.ezstock.dto.CommentRequest;
import com.ezstock.dto.LikeResponse;
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
    default PostPageResponse listPosts(String stockId, int page) {
        return listPosts(stockId, PostSort.LATEST, page);
    }

    /** 熱門：最近 30 天的文章依熱度分數排序；最新：依發文時間 */
    PostPageResponse listPosts(String stockId, PostSort sort, int page);

    PostDetailResponse getPost(long postId, Long viewerId);

    /**
     * 打開文章內頁：跟 getPost 一樣，另外記一次瀏覽（同一個人 30 分鐘內只算一次）。
     *
     * @param clientIp 沒登入時用 IP 判斷是不是同一個人
     */
    PostDetailResponse viewPost(long postId, Long viewerId, String clientIp);

    /** 按讚；已經按過就維持原狀 */
    LikeResponse like(long memberId, long postId);

    /** 收回讚；本來就沒按也不會出錯 */
    LikeResponse unlike(long memberId, long postId);

    PostDetailResponse createPost(long memberId, PostRequest request);

    PostDetailResponse updatePost(long memberId, long postId, PostRequest request);

    void deletePost(long memberId, long postId);

    /** 留言後回傳最新的文章內頁（含所有留言） */
    PostDetailResponse addComment(long memberId, long postId, CommentRequest request);

    PostDetailResponse deleteComment(long memberId, long commentId);
}
