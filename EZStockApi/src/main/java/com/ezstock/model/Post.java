package com.ezstock.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 論壇文章。
 * 標記的股票用 @ElementCollection 存在 post_stock 表；@BatchSize 讓列表頁一次查出所有文章的股票，
 * 而不是每篇文章各查一次（N+1）。
 */
@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 10000)
    private String content;

    @ElementCollection
    @CollectionTable(name = "post_stock", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "stock_id", length = 10)
    @OrderBy
    @BatchSize(size = 50)
    private Set<String> stockIds = new LinkedHashSet<>();

    /**
     * updatable = false：留言數只用 UPDATE ... SET comment_count = comment_count + 1 修改。
     * 不然修改文章時，JPA 會把物件裡（可能已經過時）的留言數整個寫回去，蓋掉別人剛加的。
     */
    @Column(name = "comment_count", nullable = false, updatable = false)
    private int commentCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Post() {
    }

    public Post(Member author, String title, String content, Collection<String> stockIds, LocalDateTime now) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.stockIds = new LinkedHashSet<>(stockIds);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String title, String content, Collection<String> stockIds, LocalDateTime now) {
        this.title = title;
        this.content = content;
        this.stockIds.clear();
        this.stockIds.addAll(stockIds);
        this.updatedAt = now;
    }

    /** 軟刪除：只記下刪除時間 */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isWrittenBy(long memberId) {
        return author.getId() != null && author.getId() == memberId;
    }

    /** MyBatis 用：文章與標記的股票分兩次查，查完再放進來 */
    public void assignStockIds(Collection<String> stockIds) {
        this.stockIds = new LinkedHashSet<>(stockIds);
    }

    public Long getId() { return id; }
    public Member getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Set<String> getStockIds() { return stockIds; }
    public int getCommentCount() { return commentCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
