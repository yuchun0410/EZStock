package com.ezstock.repository;

import com.ezstock.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.postId = :postId ORDER BY c.id")
    List<Comment> findByPost(@Param("postId") Long postId);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.id = :id")
    Optional<Comment> findWithAuthor(@Param("id") Long id);
}
