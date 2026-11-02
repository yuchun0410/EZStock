package com.ezstock.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 文章的留言。只能刪除，不能修改（避免改掉別人已經回覆過的內容）。
 */
@Entity
@Table(name = "comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Comment() {
    }

    public Comment(long postId, Member author, String content, LocalDateTime now) {
        this.postId = postId;
        this.author = author;
        this.content = content;
        this.createdAt = now;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isWrittenBy(long memberId) {
        return author.getId() != null && author.getId() == memberId;
    }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public Member getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
