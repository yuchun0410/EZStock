package com.ezstock.controller;

import com.ezstock.dto.PostDetailResponse;
import com.ezstock.service.ForumService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 刪除留言（要登入、只能刪自己的），回傳最新的文章內頁 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final ForumService forumService;

    public CommentController(ForumService forumService) {
        this.forumService = forumService;
    }

    @DeleteMapping("/{commentId}")
    public PostDetailResponse delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long commentId) {
        return forumService.deleteComment(PostController.memberId(jwt), commentId);
    }
}
