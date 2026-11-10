package com.ezstock.dao.mybatis;

import com.ezstock.dao.PageResult;
import com.ezstock.dao.PostDao;
import com.ezstock.dao.mybatis.mapper.PostMapper;
import com.ezstock.model.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文章和標記的股票分兩次查：先查一頁文章（LIMIT 才不會被 JOIN 出來的多列影響），
 * 再用 IN (...) 一次查出這頁所有文章的股票，在程式裡組起來。總共 3 句 SQL，不會 N+1。
 */
@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class PostMyBatisDao implements PostDao {

    private static final Logger log = LoggerFactory.getLogger(PostMyBatisDao.class);

    private final PostMapper mapper;

    public PostMyBatisDao(PostMapper mapper) {
        this.mapper = mapper;
        log.info("PostDao 使用 MyBatis 實作");
    }

    @Override
    public PageResult<Post> findPage(String stockId, int page, int size) {
        List<Post> posts = mapper.findPage(stockId, page * size, size);
        long total = mapper.count(stockId);
        attachStockIds(posts);
        log.debug("[MyBatis] 查詢文章 股票={} 第 {} 頁，本頁 {} 篇，共 {} 篇",
                stockId == null ? "全部" : stockId, page, posts.size(), total);
        return new PageResult<>(posts, total);
    }

    @Override
    public Optional<Post> findById(long id) {
        Post post = mapper.findById(id);
        if (post != null) {
            attachStockIds(List.of(post));
        }
        return Optional.ofNullable(post);
    }

    @Override
    @Transactional
    public Post save(Post post) {
        boolean isNew = post.getId() == null;
        if (isNew) {
            mapper.insert(post);
        } else {
            mapper.update(post);
            mapper.deleteStockIds(post.getId());
        }
        if (!post.getStockIds().isEmpty()) {
            mapper.insertStockIds(post.getId(), post.getStockIds());
        }
        log.debug("[MyBatis] {}文章 id={}", isNew ? "新增" : "更新", post.getId());
        return post;
    }

    @Override
    @Transactional
    public void addCommentCount(long postId, int delta) {
        mapper.addCommentCount(postId, delta);
        log.debug("[MyBatis] 文章 id={} 留言數 {}{}", postId, delta > 0 ? "+" : "", delta);
    }

    @Override
    public List<Post> findRecent(String stockId, LocalDateTime since, int limit) {
        List<Post> posts = mapper.findRecent(stockId, since, limit);
        attachStockIds(posts);
        log.debug("[MyBatis] 熱門候選 股票={} {} 之後的文章 {} 篇", stockId == null ? "全部" : stockId, since, posts.size());
        return posts;
    }

    @Override
    @Transactional
    public boolean addLike(long postId, long memberId, LocalDateTime now) {
        return mapper.insertLike(postId, memberId, now) == 1;
    }

    @Override
    @Transactional
    public boolean removeLike(long postId, long memberId) {
        return mapper.deleteLike(postId, memberId) == 1;
    }

    @Override
    public boolean hasLiked(long postId, long memberId) {
        return mapper.countLike(postId, memberId) > 0;
    }

    @Override
    @Transactional
    public void addLikeCount(long postId, int delta) {
        mapper.addLikeCount(postId, delta);
        log.debug("[MyBatis] 文章 id={} 讚數 {}{}", postId, delta > 0 ? "+" : "", delta);
    }

    @Override
    @Transactional
    public void incrementViewCount(long postId) {
        mapper.incrementViewCount(postId);
    }

    private void attachStockIds(List<Post> posts) {
        if (posts.isEmpty()) {
            return;
        }
        Map<Long, List<String>> byPost = new HashMap<>();
        for (Map<String, Object> row : mapper.findStockIds(posts.stream().map(Post::getId).toList())) {
            byPost.computeIfAbsent(((Number) row.get("post_id")).longValue(), id -> new ArrayList<>())
                    .add((String) row.get("stock_id"));
        }
        posts.forEach(post -> post.assignStockIds(byPost.getOrDefault(post.getId(), List.of())));
    }
}
