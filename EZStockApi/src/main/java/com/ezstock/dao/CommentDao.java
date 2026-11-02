package com.ezstock.dao;

import com.ezstock.model.Comment;

import java.util.List;
import java.util.Optional;

/**
 * 留言的存取介面。
 */
public interface CommentDao {

    /** 某篇文章的所有留言（含已刪除的，畫面顯示「此留言已刪除」），由舊到新 */
    List<Comment> findByPost(long postId);

    Optional<Comment> findById(long id);

    Comment save(Comment comment);
}
