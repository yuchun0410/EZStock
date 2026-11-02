package com.ezstock.repository;

import com.ezstock.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
