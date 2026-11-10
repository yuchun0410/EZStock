package com.ezstock.controller;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.LikeResponse;
import com.ezstock.dto.PostPageResponse;
import com.ezstock.exception.ForbiddenException;
import com.ezstock.exception.GlobalExceptionHandler;
import com.ezstock.service.ForumService;
import com.ezstock.service.PostSort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostControllerTest {

    private ForumService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ForumService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PostController(service), new CommentController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("列表：沒登入也能看，股票、排序與頁數從參數帶入；沒給排序是最新")
    void list() throws Exception {
        when(service.listPosts("2330", PostSort.HOT, 1)).thenReturn(new PostPageResponse(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/posts").param("stockId", "2330").param("sort", "hot").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));

        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
        verify(service).listPosts(null, PostSort.LATEST, 0);
    }

    @Test
    @DisplayName("文章內頁：沒登入時 viewerId 是 null；登入時用 JWT 的會員 id")
    void viewer() throws Exception {
        mockMvc.perform(get("/api/posts/10")).andExpect(status().isOk());
        verify(service).viewPost(eq(10L), isNull(), anyString());

        login(7);
        mockMvc.perform(get("/api/posts/10")).andExpect(status().isOk());
        verify(service).viewPost(eq(10L), eq(7L), anyString());
    }

    @Test
    @DisplayName("按讚、收回讚：用 JWT 的會員 id")
    void like() throws Exception {
        login(7);
        when(service.like(7L, 10L)).thenReturn(new LikeResponse(true, 3));

        mockMvc.perform(post("/api/posts/10/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(3));

        mockMvc.perform(delete("/api/posts/10/like")).andExpect(status().isOk());
        verify(service).unlike(7L, 10L);
    }

    @Test
    @DisplayName("發文：欄位不合法回 400；超過 5 檔股票也是 400")
    void createValidation() throws Exception {
        login(7);
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"內容\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("請輸入標題")));
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"stockIds\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).createPost(anyLong(), any());
    }

    @Test
    @DisplayName("刪別人的文章：403；刪自己的：204")
    void delete403() throws Exception {
        login(7);
        doThrow(new ForbiddenException("只有作者可以刪除文章")).when(service).deletePost(7L, 10L);

        mockMvc.perform(delete("/api/posts/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("只有作者可以刪除文章"));
        mockMvc.perform(delete("/api/posts/11")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("留言、刪除留言：用 JWT 的會員 id")
    void comments() throws Exception {
        login(7);
        mockMvc.perform(post("/api/posts/10/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"推\"}"))
                .andExpect(status().isCreated());
        verify(service).addComment(eq(7L), eq(10L), any());

        mockMvc.perform(delete("/api/comments/3")).andExpect(status().isOk());
        verify(service).deleteComment(7L, 3L);
    }

    private static void login(long memberId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(String.valueOf(memberId)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
