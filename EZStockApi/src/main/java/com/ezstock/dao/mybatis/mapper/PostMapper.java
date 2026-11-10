package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface PostMapper {

    List<Post> findPage(@Param("stockId") String stockId, @Param("offset") int offset, @Param("limit") int limit);

    long count(@Param("stockId") String stockId);

    Post findById(@Param("id") long id);

    /** 一次查出好幾篇文章標記的股票：每一列是 {post_id, stock_id} */
    List<Map<String, Object>> findStockIds(@Param("postIds") Collection<Long> postIds);

    int insert(Post post);

    int update(Post post);

    int deleteStockIds(@Param("postId") long postId);

    int insertStockIds(@Param("postId") long postId, @Param("stockIds") Collection<String> stockIds);

    int addCommentCount(@Param("id") long id, @Param("delta") int delta);

    List<Post> findRecent(@Param("stockId") String stockId, @Param("since") LocalDateTime since,
                          @Param("limit") int limit);

    int insertLike(@Param("postId") long postId, @Param("memberId") long memberId, @Param("now") LocalDateTime now);

    int deleteLike(@Param("postId") long postId, @Param("memberId") long memberId);

    long countLike(@Param("postId") long postId, @Param("memberId") long memberId);

    int addLikeCount(@Param("id") long id, @Param("delta") int delta);

    int incrementViewCount(@Param("id") long id);
}
