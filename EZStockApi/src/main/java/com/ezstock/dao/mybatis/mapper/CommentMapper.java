package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    List<Comment> findByPost(@Param("postId") long postId);

    Comment findById(@Param("id") long id);

    int insert(Comment comment);

    /** 留言只能刪除，所以只更新 deleted_at */
    int updateDeletedAt(Comment comment);
}
