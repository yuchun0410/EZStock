package com.ezstock.controller;

import com.ezstock.dto.CommentRequest;
import com.ezstock.dto.LikeResponse;
import com.ezstock.dto.PostDetailResponse;
import com.ezstock.dto.PostPageResponse;
import com.ezstock.dto.PostRequest;
import com.ezstock.service.ForumService;
import com.ezstock.service.PostSort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 論壇文章。GET 不用登入（jwt 會是 null），其他都要登入（SecurityConfig 設定）。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final ForumService forumService;

    public PostController(ForumService forumService) {
        this.forumService = forumService;
    }

    /** 例如 /api/posts?sort=hot&stockId=2330&page=0；sort 是 hot（熱門）或 latest（最新，預設） */
    @GetMapping
    public PostPageResponse list(@RequestParam(required = false) String stockId,
                                 @RequestParam(required = false) String sort,
                                 @RequestParam(defaultValue = "0") int page) {
        return forumService.listPosts(stockId, PostSort.parse(sort), page);
    }

    /** 文章內頁，順便記一次瀏覽。IP 用 getRemoteAddr()，跟登入次數限制一樣，不自己讀可偽造的 header */
    @GetMapping("/{postId}")
    public PostDetailResponse get(@PathVariable long postId, @AuthenticationPrincipal Jwt jwt,
                                  HttpServletRequest http) {
        return forumService.viewPost(postId, jwt == null ? null : memberId(jwt), http.getRemoteAddr());
    }

    @PostMapping("/{postId}/like")
    public LikeResponse like(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
        return forumService.like(memberId(jwt), postId);
    }

    @DeleteMapping("/{postId}/like")
    public LikeResponse unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
        return forumService.unlike(memberId(jwt), postId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PostRequest request) {
        return forumService.createPost(memberId(jwt), request);
    }

    @PutMapping("/{postId}")
    public PostDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId,
                                     @Valid @RequestBody PostRequest request) {
        return forumService.updatePost(memberId(jwt), postId, request);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
        forumService.deletePost(memberId(jwt), postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailResponse comment(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId,
                                      @Valid @RequestBody CommentRequest request) {
        return forumService.addComment(memberId(jwt), postId, request);
    }

    static long memberId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
