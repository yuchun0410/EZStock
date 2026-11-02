package com.ezstock.dao;

import com.ezstock.model.Post;

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
}
