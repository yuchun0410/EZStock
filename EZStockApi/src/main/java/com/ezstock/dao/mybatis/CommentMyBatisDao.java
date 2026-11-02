package com.ezstock.dao.mybatis;

import com.ezstock.dao.CommentDao;
import com.ezstock.dao.mybatis.mapper.CommentMapper;
import com.ezstock.model.Comment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class CommentMyBatisDao implements CommentDao {

    private static final Logger log = LoggerFactory.getLogger(CommentMyBatisDao.class);

    private final CommentMapper mapper;

    public CommentMyBatisDao(CommentMapper mapper) {
        this.mapper = mapper;
        log.info("CommentDao 使用 MyBatis 實作");
    }

    @Override
    public List<Comment> findByPost(long postId) {
        List<Comment> comments = mapper.findByPost(postId);
        log.debug("[MyBatis] 查詢留言 文章 id={} 共 {} 則", postId, comments.size());
        return comments;
    }

    @Override
    public Optional<Comment> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    @Transactional
    public Comment save(Comment comment) {
        if (comment.getId() == null) {
            mapper.insert(comment);
            log.debug("[MyBatis] 新增留言 id={} 文章 id={}", comment.getId(), comment.getPostId());
        } else {
            mapper.updateDeletedAt(comment);
            log.debug("[MyBatis] 更新留言 id={}", comment.getId());
        }
        return comment;
    }
}
