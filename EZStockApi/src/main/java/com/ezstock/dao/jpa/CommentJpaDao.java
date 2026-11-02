package com.ezstock.dao.jpa;

import com.ezstock.dao.CommentDao;
import com.ezstock.model.Comment;
import com.ezstock.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class CommentJpaDao implements CommentDao {

    private static final Logger log = LoggerFactory.getLogger(CommentJpaDao.class);

    private final CommentRepository repository;

    public CommentJpaDao(CommentRepository repository) {
        this.repository = repository;
        log.info("CommentDao 使用 JPA 實作");
    }

    @Override
    public List<Comment> findByPost(long postId) {
        List<Comment> comments = repository.findByPost(postId);
        log.debug("[JPA] 查詢留言 文章 id={} 共 {} 則", postId, comments.size());
        return comments;
    }

    @Override
    public Optional<Comment> findById(long id) {
        return repository.findWithAuthor(id);
    }

    @Override
    @Transactional
    public Comment save(Comment comment) {
        boolean isNew = comment.getId() == null;
        Comment saved = repository.save(comment);
        log.debug("[JPA] {}留言 id={} 文章 id={}", isNew ? "新增" : "更新", saved.getId(), saved.getPostId());
        return saved;
    }
}
