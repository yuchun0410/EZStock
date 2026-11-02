package com.ezstock.service.impl;

import com.ezstock.dao.CommentDao;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.MemberDao;
import com.ezstock.dao.PageResult;
import com.ezstock.dao.PostDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.CommentRequest;
import com.ezstock.dto.CommentResponse;
import com.ezstock.dto.PostDetailResponse;
import com.ezstock.dto.PostPageResponse;
import com.ezstock.dto.PostRequest;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ForbiddenException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.Comment;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Member;
import com.ezstock.model.Post;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForumServiceImplTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 2, 14, 0);

    private final Member alice = member(1L, "Alice");
    private final Member bob = member(2L, "Bob");

    private PostDao postDao;
    private CommentDao commentDao;
    private MemberDao memberDao;
    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private ForumServiceImpl service;

    @BeforeEach
    void setUp() {
        postDao = mock(PostDao.class);
        commentDao = mock(CommentDao.class);
        memberDao = mock(MemberDao.class);
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T07:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new ForumServiceImpl(postDao, commentDao, memberDao, stockDao, dailyPriceDao, clock);

        when(memberDao.findById(1L)).thenReturn(Optional.of(alice));
        when(memberDao.findById(2L)).thenReturn(Optional.of(bob));
        when(stockDao.findByIds(any())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return ids.stream().filter(id -> !id.startsWith("99"))
                    .map(id -> new Stock(id, "股票" + id, StockType.STOCK, "測試")).toList();
        });
        when(postDao.save(any())).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            if (post.getId() == null) {
                ReflectionTestUtils.setField(post, "id", 100L);
            }
            return post;
        });
        when(commentDao.save(any())).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            if (comment.getId() == null) {
                ReflectionTestUtils.setField(comment, "id", 500L);
            }
            return comment;
        });
        when(commentDao.findByPost(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("列表：摘要把換行合併、太長截斷，附上股票名稱，算出總頁數")
    void list() {
        Post post = post(10L, alice, "標題", "第一行\n\n第二行  " + "長".repeat(200), List.of("2330"));
        when(postDao.findPage("2330", 0, 20)).thenReturn(new PageResult<>(List.of(post), 41));

        PostPageResponse page = service.listPosts(" 2330 ", 0);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items().get(0).excerpt()).startsWith("第一行 第二行 長").endsWith("…").hasSize(121);
        assertThat(page.items().get(0).stocks().get(0).name()).isEqualTo("股票2330");
        assertThat(page.items().get(0).author().displayName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("發文：股票代號轉大寫、去重複；查無的股票回 400")
    void create() {
        PostDetailResponse detail = service.createPost(1L,
                new PostRequest(" 台積電法說會 ", "  內容  ", List.of("2330", " 2330", "0050")));

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(postDao).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("台積電法說會");
        assertThat(saved.getValue().getContent()).isEqualTo("內容");
        assertThat(saved.getValue().getStockIds()).containsExactly("2330", "0050");
        assertThat(detail.editable()).isTrue();

        assertThatThrownBy(() -> service.createPost(1L, new PostRequest("t", "c", List.of("9999"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("修改、刪除別人的文章：403；文章已刪除：404")
    void notAuthor() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        when(postDao.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.updatePost(2L, 10L, new PostRequest("改", "改", List.of())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.deletePost(2L, 10L)).isInstanceOf(ForbiddenException.class);

        service.deletePost(1L, 10L);
        assertThat(post.isDeleted()).isTrue();
        assertThatThrownBy(() -> service.getPost(10L, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("留言：存留言並把留言數 +1；已刪除的文章不能留言")
    void addComment() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        when(postDao.findById(10L)).thenReturn(Optional.of(post));

        service.addComment(2L, 10L, new CommentRequest(" 推 "));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentDao).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("推");
        assertThat(saved.getValue().getAuthor()).isSameAs(bob);
        verify(postDao).addCommentCount(10L, 1);

        post.delete(T);
        assertThatThrownBy(() -> service.addComment(2L, 10L, new CommentRequest("推")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除留言：只能刪自己的；刪過再刪不會重複扣留言數；已刪除的留言不回傳內容")
    void deleteComment() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        Comment comment = new Comment(10L, bob, "Bob 的留言", T);
        ReflectionTestUtils.setField(comment, "id", 7L);
        when(postDao.findById(10L)).thenReturn(Optional.of(post));
        when(commentDao.findById(7L)).thenReturn(Optional.of(comment));
        when(commentDao.findByPost(10L)).thenReturn(List.of(comment));

        assertThatThrownBy(() -> service.deleteComment(1L, 7L)).isInstanceOf(ForbiddenException.class);

        PostDetailResponse detail = service.deleteComment(2L, 7L);
        service.deleteComment(2L, 7L);

        verify(postDao).addCommentCount(10L, -1);
        CommentResponse shown = detail.comments().get(0);
        assertThat(shown.deleted()).isTrue();
        assertThat(shown.content()).isNull();
        assertThat(shown.author()).isNull();
        assertThat(detail.commentCount()).isZero();
    }

    @Test
    @DisplayName("文章內頁：沒登入時 editable、deletable 都是 false")
    void anonymousViewer() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        Comment comment = new Comment(10L, alice, "自己留言", T);
        ReflectionTestUtils.setField(comment, "id", 8L);
        when(postDao.findById(10L)).thenReturn(Optional.of(post));
        when(commentDao.findByPost(10L)).thenReturn(List.of(comment));

        PostDetailResponse anonymous = service.getPost(10L, null);
        PostDetailResponse asAuthor = service.getPost(10L, 1L);

        assertThat(anonymous.editable()).isFalse();
        assertThat(anonymous.comments().get(0).deletable()).isFalse();
        assertThat(asAuthor.editable()).isTrue();
        assertThat(asAuthor.comments().get(0).deletable()).isTrue();
        verify(postDao, never()).addCommentCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("股票標籤：附上最新收盤與漲跌幅，只查一次日線；沒有股價的不顯示漲跌")
    void stockQuote() {
        Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
        when(dailyPriceDao.findByStocksSince(any(), any())).thenReturn(List.of(
                price(tsmc, LocalDate.of(2026, 10, 29), "2340"),
                price(tsmc, LocalDate.of(2026, 10, 30), "2370")));
        Post withQuote = post(10L, alice, "a", "b", List.of("2330", "0050"));
        when(postDao.findPage(null, 0, 20)).thenReturn(new PageResult<>(List.of(withQuote), 1));

        var stocks = service.listPosts(null, 0).items().get(0).stocks();

        assertThat(stocks.get(0).close()).isEqualByComparingTo("2370");
        assertThat(stocks.get(0).change()).isEqualByComparingTo("30");
        assertThat(stocks.get(0).changePercent()).isEqualByComparingTo("1.28");
        assertThat(stocks.get(0).priceDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(stocks.get(1).name()).isEqualTo("股票0050");
        assertThat(stocks.get(1).close()).isNull();
        verify(dailyPriceDao).findByStocksSince(any(), eq(LocalDate.of(2026, 10, 19)));
    }

    private static DailyPrice price(Stock stock, LocalDate date, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(stock, date, c, c, c, c, 1_000L);
    }

    private static Member member(long id, String name) {
        Member member = Member.withPassword(name + "@example.com", "hash", name, T);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static Post post(long id, Member author, String title, String content, List<String> stockIds) {
        Post post = new Post(author, title, content, stockIds, T);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
