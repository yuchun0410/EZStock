package com.ezstock.dao;

import com.ezstock.model.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 論壇文章的存取介面。
 */
public interface PostDao {

    /**
     * 沒被刪除的文章，由新到舊，含作者與標記的股票。
     *
     * @param stockId 只看標記了這檔股票的文章；null 代表全部
     * @param page    第幾頁，從 0 開始
     */
    PageResult<Post> findPage(String stockId, int page, int size);

    /** 含已刪除的文章（由 Service 決定怎麼顯示） */
    Optional<Post> findById(long id);

    /** 沒有 id 就新增，有 id 就更新（含標記的股票） */
    Post save(Post post);

    /** 留言數加減，用一句 UPDATE ... SET comment_count = comment_count + ? 完成，兩個人同時留言也不會少算 */
    void addCommentCount(long postId, int delta);

    /**
     * 熱門文章的候選：最近 since 之後發的、沒被刪除的文章，由新到舊，最多 limit 篇（含作者與標記的股票）。
     * 熱度分數在 Service 算，因為分數跟「現在幾點」有關，每次查詢都不一樣。
     */
    List<Post> findRecent(String stockId, LocalDateTime since, int limit);

    /** 按讚：已經按過就什麼都不做。回傳 true 代表這次真的新增了（要把讚數 +1） */
    boolean addLike(long postId, long memberId, LocalDateTime now);

    /** 收回讚：回傳 true 代表真的刪掉了（要把讚數 −1） */
    boolean removeLike(long postId, long memberId);

    boolean hasLiked(long postId, long memberId);

    /** 讚數加減，原子更新 */
    void addLikeCount(long postId, int delta);

    /** 瀏覽數 +1，原子更新 */
    void incrementViewCount(long postId);
}
