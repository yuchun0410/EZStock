package com.ezstock.dao.jpa;

import com.ezstock.dao.PageResult;
import com.ezstock.dao.PostDao;
import com.ezstock.model.Post;
import com.ezstock.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class PostJpaDao implements PostDao {

    private static final Logger log = LoggerFactory.getLogger(PostJpaDao.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PostRepository repository;

    public PostJpaDao(PostRepository repository) {
        this.repository = repository;
        log.info("PostDao 使用 JPA 實作");
    }

    /** readOnly 交易：讓 LAZY 的股票清單在 DAO 裡就載入（@BatchSize 一次查完），離開 DAO 後還能讀 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<Post> findPage(String stockId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Post> result = stockId == null
                ? repository.findActive(pageable)
                : repository.findActiveByStock(stockId, pageable);
        result.getContent().forEach(post -> post.getStockIds().size());
        log.debug("[JPA] 查詢文章 股票={} 第 {} 頁，本頁 {} 篇，共 {} 篇",
                stockId == null ? "全部" : stockId, page, result.getNumberOfElements(), result.getTotalElements());
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Post> findById(long id) {
        Optional<Post> post = repository.findWithAuthor(id);
        post.ifPresent(p -> p.getStockIds().size());
        return post;
    }

    @Override
    @Transactional
    public Post save(Post post) {
        boolean isNew = post.getId() == null;
        Post saved = repository.save(post);
        log.debug("[JPA] {}文章 id={}", isNew ? "新增" : "更新", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void addCommentCount(long postId, int delta) {
        repository.addCommentCount(postId, delta);
        log.debug("[JPA] 文章 id={} 留言數 {}{}", postId, delta > 0 ? "+" : "", delta);
    }
}
