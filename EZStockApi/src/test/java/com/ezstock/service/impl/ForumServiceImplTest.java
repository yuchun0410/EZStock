package com.ezstock.service.impl;

import com.ezstock.config.ForumProperties;
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
import com.ezstock.dto.PostSummaryResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ForbiddenException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.Comment;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Member;
import com.ezstock.model.Post;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import com.ezstock.service.PostSort;
import com.ezstock.service.PostViewTracker;
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
    private PostViewTracker viewTracker;
    private ForumServiceImpl service;

    @BeforeEach
    void setUp() {
        postDao = mock(PostDao.class);
        commentDao = mock(CommentDao.class);
        memberDao = mock(MemberDao.class);
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T07:00:00Z"), ZoneId.of("Asia/Taipei"));
        viewTracker = mock(PostViewTracker.class);
        service = new ForumServiceImpl(postDao, commentDao, memberDao, stockDao, dailyPriceDao, viewTracker,
                new ForumProperties(null), clock);

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

    @Test
    @DisplayName("熱門：幾天前有讚有留言的文章，排在剛發、只有 1 次瀏覽的文章前面；都沒互動的依時間，新的在前")
    void hotOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 15, 0);
        // 實際遇到的情況：8 天前 1 讚 1 留言 1 瀏覽 vs 剛發 1 瀏覽
        Post weekOld = post(1L, alice, "台積電還好嗎？", "內容", List.of());
        Post fresh = post(2L, alice, "剛發", "內容", List.of());
        Post quietNew = post(3L, alice, "沒人看的新文", "內容", List.of());
        Post quietOld = post(4L, alice, "沒人看的舊文", "內容", List.of());
        ReflectionTestUtils.setField(weekOld, "createdAt", now.minusDays(8));
        ReflectionTestUtils.setField(weekOld, "likeCount", 1);
        ReflectionTestUtils.setField(weekOld, "commentCount", 1);
        ReflectionTestUtils.setField(weekOld, "viewCount", 1);
        ReflectionTestUtils.setField(fresh, "createdAt", now.minusMinutes(1));
        ReflectionTestUtils.setField(fresh, "viewCount", 1);
        ReflectionTestUtils.setField(quietNew, "createdAt", now.minusHours(2));
        ReflectionTestUtils.setField(quietOld, "createdAt", now.minusDays(3));
        when(postDao.findRecent(eq(null), any(), eq(ForumServiceImpl.HOT_CANDIDATES)))
                .thenReturn(List.of(fresh, quietOld, weekOld, quietNew));

        PostPageResponse page = service.listPosts(null, PostSort.HOT, 0);

        // 沒互動的兩篇都是 0 分，同分依發文時間，新的在前
        assertThat(page.items()).extracting(PostSummaryResponse::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(page.total()).isEqualTo(4);
        verify(postDao, never()).findPage(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("熱度分數：時間越久分數越低；互動越多分數越高")
    void hotScore() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 15, 0);
        double g = ForumProperties.DEFAULT_HOT_GRAVITY;
        assertThat(HotScore.of(5, 0, 0, now.minusHours(1), now, g))
                .isGreaterThan(HotScore.of(5, 0, 0, now.minusHours(10), now, g));
        assertThat(HotScore.of(10, 0, 0, now.minusHours(5), now, g))
                .isGreaterThan(HotScore.of(1, 0, 0, now.minusHours(5), now, g));
        // 衰減越強，舊文章掉得越快
        assertThat(HotScore.of(5, 0, 0, now.minusDays(3), now, 1.5))
                .isLessThan(HotScore.of(5, 0, 0, now.minusDays(3), now, g));
        // 剛發的文章（0 互動、0 小時）不會除以 0
        assertThat(HotScore.of(0, 0, 0, now, now, g)).isZero();
    }

    @Test
    @DisplayName("瀏覽：第一次看才 +1；30 分鐘內再看不加；有登入用會員 id，沒登入用 IP 判斷")
    void viewCount() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        when(postDao.findById(10L)).thenReturn(Optional.of(post));
        when(viewTracker.isFirstView(10L, "member:2")).thenReturn(true, false);
        when(viewTracker.isFirstView(10L, "ip:1.2.3.4")).thenReturn(true);

        service.viewPost(10L, 2L, "1.2.3.4");
        service.viewPost(10L, 2L, "1.2.3.4");
        service.viewPost(10L, null, "1.2.3.4");

        verify(postDao, org.mockito.Mockito.times(2)).incrementViewCount(10L);
    }

    @Test
    @DisplayName("按讚：第一次按讚數 +1；已經按過不再加；收回讚 −1，沒按過收回也不會變負的")
    void like() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        when(postDao.findById(10L)).thenReturn(Optional.of(post));
        when(postDao.addLike(eq(10L), eq(2L), any())).thenReturn(true, false);
        when(postDao.removeLike(10L, 2L)).thenReturn(true, false);

        assertThat(service.like(2L, 10L).liked()).isTrue();
        service.like(2L, 10L);
        verify(postDao, org.mockito.Mockito.times(1)).addLikeCount(10L, 1);

        assertThat(service.unlike(2L, 10L).liked()).isFalse();
        service.unlike(2L, 10L);
        verify(postDao, org.mockito.Mockito.times(1)).addLikeCount(10L, -1);
    }

    @Test
    @DisplayName("按讚：文章已刪除回 404")
    void likeDeletedPost() {
        Post post = post(10L, alice, "標題", "內容", List.of());
        post.delete(T);
        when(postDao.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.like(2L, 10L)).isInstanceOf(ResourceNotFoundException.class);
        verify(postDao, never()).addLike(anyLong(), anyLong(), any());
    }

    private static Post post(long id, Member author, String title, String content, List<String> stockIds) {
        Post post = new Post(author, title, content, stockIds, T);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
