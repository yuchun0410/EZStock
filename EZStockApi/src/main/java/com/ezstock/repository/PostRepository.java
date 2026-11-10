package com.ezstock.repository;

import com.ezstock.model.Post;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** JOIN FETCH 作者；有 JOIN FETCH 時分頁要另外寫 countQuery，不然 Spring 自動產生的 COUNT 會出錯 */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.deletedAt IS NULL",
            countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL")
    Page<Post> findActive(Pageable pageable);

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author "
            + "WHERE p.deletedAt IS NULL AND :stockId MEMBER OF p.stockIds",
            countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND :stockId MEMBER OF p.stockIds")
    Page<Post> findActiveByStock(@Param("stockId") String stockId, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
    Optional<Post> findWithAuthor(@Param("id") Long id);

    /** clearAutomatically：更新完清掉快取，之後再查文章才會拿到新的留言數 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id")
    int addCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /** 熱門文章的候選：最近一段時間的文章，由新到舊；Limit 讓資料庫只回傳前幾篇 */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.deletedAt IS NULL AND p.createdAt >= :since "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findRecent(@Param("since") LocalDateTime since, Limit limit);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.deletedAt IS NULL AND p.createdAt >= :since "
            + "AND :stockId MEMBER OF p.stockIds ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findRecentByStock(@Param("stockId") String stockId, @Param("since") LocalDateTime since, Limit limit);

    /**
     * 按讚用 INSERT IGNORE：主鍵 (post_id, member_id) 已經存在就略過，回傳 0；新增成功回傳 1。
     * 同一個人連點兩下、兩個請求同時進來，也只會有一筆，不用先查再寫（先查再寫會有競爭問題）。
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO post_like (post_id, member_id, created_at) VALUES (:postId, :memberId, :now)",
            nativeQuery = true)
    int insertLike(@Param("postId") long postId, @Param("memberId") long memberId, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = "DELETE FROM post_like WHERE post_id = :postId AND member_id = :memberId", nativeQuery = true)
    int deleteLike(@Param("postId") long postId, @Param("memberId") long memberId);

    @Query(value = "SELECT COUNT(*) FROM post_like WHERE post_id = :postId AND member_id = :memberId", nativeQuery = true)
    long countLike(@Param("postId") long postId, @Param("memberId") long memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id")
    int addLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
