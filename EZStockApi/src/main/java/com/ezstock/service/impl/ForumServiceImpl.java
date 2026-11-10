package com.ezstock.service.impl;

import com.ezstock.config.ForumProperties;
import com.ezstock.dao.CommentDao;
import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.MemberDao;
import com.ezstock.dao.PageResult;
import com.ezstock.dao.PostDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dto.AuthorResponse;
import com.ezstock.dto.CommentRequest;
import com.ezstock.dto.CommentResponse;
import com.ezstock.dto.LikeResponse;
import com.ezstock.dto.PostDetailResponse;
import com.ezstock.dto.PostPageResponse;
import com.ezstock.dto.PostRequest;
import com.ezstock.dto.PostSummaryResponse;
import com.ezstock.dto.StockTag;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ForbiddenException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.exception.UnauthorizedException;
import com.ezstock.model.Comment;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Member;
import com.ezstock.model.Post;
import com.ezstock.model.Stock;
import com.ezstock.service.ForumService;
import com.ezstock.service.PostSort;
import com.ezstock.service.PostViewTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 論壇。
 * - 沒登入也能看；發文、留言要登入
 * - 只有作者能修改、刪除自己的文章和留言（不是作者回 403）
 * - 已刪除的文章回 404；已刪除的留言保留位置，內容不回傳
 * - log 只記 id 和字數，不記內容
 */
@Service
public class ForumServiceImpl implements ForumService {

    private static final Logger log = LoggerFactory.getLogger(ForumServiceImpl.class);
    private static final int EXCERPT_LENGTH = 120;
    /** 熱門文章只從最近 30 天、最多 200 篇裡挑：文章量大的時候，改成定時算好分數存起來（或用 Redis 排行榜） */
    static final int HOT_WINDOW_DAYS = 30;
    static final int HOT_CANDIDATES = 200;
    /** 只看最近 14 天的股價：要找到最新和前一個交易日，連假也夠用；太久沒交易的股票就不顯示漲跌 */
    private static final int QUOTE_LOOKBACK_DAYS = 14;

    private final PostDao postDao;
    private final CommentDao commentDao;
    private final MemberDao memberDao;
    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final PostViewTracker viewTracker;
    private final ForumProperties forumProperties;
    private final Clock clock;

    public ForumServiceImpl(PostDao postDao, CommentDao commentDao, MemberDao memberDao, StockDao stockDao,
                            DailyPriceDao dailyPriceDao, PostViewTracker viewTracker,
                            ForumProperties forumProperties, Clock clock) {
        this.postDao = postDao;
        this.commentDao = commentDao;
        this.memberDao = memberDao;
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.viewTracker = viewTracker;
        this.forumProperties = forumProperties;
        this.clock = clock;
    }

    @Override
    public PostPageResponse listPosts(String stockId, PostSort sort, int page) {
        if (page < 0) {
            throw new BadRequestException("頁數不能是負數");
        }
        String filter = stockId == null || stockId.isBlank() ? null : stockId.trim().toUpperCase();
        PageResult<Post> result = sort == PostSort.HOT ? hotPage(filter, page) : postDao.findPage(filter, page, PAGE_SIZE);
        Map<String, StockTag> quotes = stockTags(result.items().stream()
                .flatMap(post -> post.getStockIds().stream()).collect(Collectors.toSet()));

        List<PostSummaryResponse> items = result.items().stream()
                .map(post -> new PostSummaryResponse(post.getId(), post.getTitle(), excerpt(post.getContent()),
                        AuthorResponse.from(post.getAuthor()), tags(post.getStockIds(), quotes),
                        post.getCommentCount(), post.getLikeCount(), post.getViewCount(),
                        post.getCreatedAt(), isEdited(post)))
                .toList();
        int totalPages = (int) ((result.total() + PAGE_SIZE - 1) / PAGE_SIZE);
        log.info("[論壇] 文章列表（{}）股票={} 第 {} 頁，本頁 {} 篇，共 {} 篇", sort == PostSort.HOT ? "熱門" : "最新",
                filter == null ? "全部" : filter, page, items.size(), result.total());
        return new PostPageResponse(items, page, PAGE_SIZE, result.total(), totalPages);
    }

    /**
     * 熱門：先撈最近 30 天的文章（最多 200 篇），在程式裡算熱度分數排序，再切出這一頁。
     * 分數跟「現在幾點」有關，每次都要重算，不能直接用 SQL 的 ORDER BY 欄位。
     */
    private PageResult<Post> hotPage(String stockId, int page) {
        LocalDateTime now = LocalDateTime.now(clock);
        double gravity = forumProperties.hotGravity();
        List<Post> candidates = postDao.findRecent(stockId, now.minusDays(HOT_WINDOW_DAYS), HOT_CANDIDATES);
        List<Post> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble((Post post) -> HotScore.of(post, now, gravity)).reversed()
                        .thenComparing(Post::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(Post::getId, Comparator.reverseOrder()))
                .toList();
        int from = Math.min(page * PAGE_SIZE, sorted.size());
        int to = Math.min(from + PAGE_SIZE, sorted.size());
        return new PageResult<>(sorted.subList(from, to), sorted.size());
    }

    @Override
    public PostDetailResponse getPost(long postId, Long viewerId) {
        return detail(findActivePost(postId), viewerId);
    }

    @Override
    public PostDetailResponse viewPost(long postId, Long viewerId, String clientIp) {
        Post post = findActivePost(postId);
        String viewerKey = viewerId != null ? "member:" + viewerId : "ip:" + clientIp;
        if (viewTracker.isFirstView(postId, viewerKey)) {
            postDao.incrementViewCount(postId);
            log.debug("[論壇] 文章 id={} 瀏覽數 +1（{}）", postId, viewerId != null ? "會員 id=" + viewerId : "訪客");
            post = postDao.findById(postId).orElse(post);
        }
        return detail(post, viewerId);
    }

    /** 新增按讚和讚數 +1 放在同一個交易；INSERT IGNORE 沒新增（已經按過）就不加，連點兩下也只算一次 */
    @Override
    @Transactional
    public LikeResponse like(long memberId, long postId) {
        findActivePost(postId);
        if (postDao.addLike(postId, memberId, LocalDateTime.now(clock))) {
            postDao.addLikeCount(postId, 1);
            log.info("[論壇] 會員 id={} 按讚文章 id={}", memberId, postId);
        } else {
            log.debug("[論壇] 會員 id={} 已經按過文章 id={} 的讚", memberId, postId);
        }
        return new LikeResponse(true, findActivePost(postId).getLikeCount());
    }

    @Override
    @Transactional
    public LikeResponse unlike(long memberId, long postId) {
        findActivePost(postId);
        if (postDao.removeLike(postId, memberId)) {
            postDao.addLikeCount(postId, -1);
            log.info("[論壇] 會員 id={} 收回文章 id={} 的讚", memberId, postId);
        }
        return new LikeResponse(false, findActivePost(postId).getLikeCount());
    }

    @Override
    @Transactional
    public PostDetailResponse createPost(long memberId, PostRequest request) {
        Member author = findMember(memberId);
        Set<String> stockIds = validStockIds(request.stockIds());
        Post post = postDao.save(new Post(author, request.title().trim(), request.content().strip(), stockIds,
                LocalDateTime.now(clock)));
        log.info("[論壇] 會員 id={} 發文 id={}（標題 {} 字、內容 {} 字，標記 {}）", memberId, post.getId(),
                post.getTitle().length(), post.getContent().length(), stockIds);
        return detail(post, memberId);
    }

    @Override
    @Transactional
    public PostDetailResponse updatePost(long memberId, long postId, PostRequest request) {
        Post post = findActivePost(postId);
        requireAuthor(post.isWrittenBy(memberId), memberId, "修改文章", postId);
        Set<String> stockIds = validStockIds(request.stockIds());
        post.edit(request.title().trim(), request.content().strip(), stockIds, LocalDateTime.now(clock));
        postDao.save(post);
        log.info("[論壇] 會員 id={} 修改文章 id={}（標記 {}）", memberId, postId, stockIds);
        return detail(postDao.findById(postId).orElseThrow(), memberId);
    }

    @Override
    @Transactional
    public void deletePost(long memberId, long postId) {
        Post post = findActivePost(postId);
        requireAuthor(post.isWrittenBy(memberId), memberId, "刪除文章", postId);
        post.delete(LocalDateTime.now(clock));
        postDao.save(post);
        log.info("[論壇] 會員 id={} 刪除文章 id={}", memberId, postId);
    }

    /** 新增留言和留言數 +1 放在同一個交易，不會發生「留言存了，數字沒加」 */
    @Override
    @Transactional
    public PostDetailResponse addComment(long memberId, long postId, CommentRequest request) {
        findActivePost(postId);
        Member author = findMember(memberId);
        Comment comment = commentDao.save(new Comment(postId, author, request.content().strip(),
                LocalDateTime.now(clock)));
        postDao.addCommentCount(postId, 1);
        log.info("[論壇] 會員 id={} 在文章 id={} 留言 id={}（{} 字）", memberId, postId, comment.getId(),
                comment.getContent().length());
        return detail(postDao.findById(postId).orElseThrow(), memberId);
    }

    @Override
    @Transactional
    public PostDetailResponse deleteComment(long memberId, long commentId) {
        Comment comment = commentDao.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("查無留言"));
        requireAuthor(comment.isWrittenBy(memberId), memberId, "刪除留言", commentId);
        if (!comment.isDeleted()) {
            comment.delete(LocalDateTime.now(clock));
            commentDao.save(comment);
            postDao.addCommentCount(comment.getPostId(), -1);
            log.info("[論壇] 會員 id={} 刪除留言 id={}", memberId, commentId);
        }
        return detail(findActivePost(comment.getPostId()), memberId);
    }

    private PostDetailResponse detail(Post post, Long viewerId) {
        List<CommentResponse> comments = commentDao.findByPost(post.getId()).stream()
                .map(comment -> comment.isDeleted()
                        ? new CommentResponse(comment.getId(), null, null, true, comment.getCreatedAt(), false)
                        : new CommentResponse(comment.getId(), AuthorResponse.from(comment.getAuthor()),
                        comment.getContent(), false, comment.getCreatedAt(),
                        viewerId != null && comment.isWrittenBy(viewerId)))
                .toList();
        return new PostDetailResponse(post.getId(), post.getTitle(), post.getContent(),
                AuthorResponse.from(post.getAuthor()), tags(post.getStockIds(), stockTags(post.getStockIds())),
                (int) comments.stream().filter(c -> !c.deleted()).count(),
                post.getLikeCount(), post.getViewCount(),
                viewerId != null && postDao.hasLiked(post.getId(), viewerId),
                post.getCreatedAt(), post.getUpdatedAt(), isEdited(post),
                viewerId != null && post.isWrittenBy(viewerId), comments);
    }

    private Post findActivePost(long postId) {
        Post post = postDao.findById(postId).orElse(null);
        if (post == null || post.isDeleted()) {
            throw new ResourceNotFoundException("文章不存在或已刪除");
        }
        return post;
    }

    private Member findMember(long memberId) {
        return memberDao.findById(memberId).orElseThrow(() -> new UnauthorizedException("帳號不存在，請重新登入"));
    }

    private static void requireAuthor(boolean isAuthor, long memberId, String action, long targetId) {
        if (!isAuthor) {
            log.warn("[論壇] 會員 id={} 想{} id={}，但不是作者", memberId, action, targetId);
            throw new ForbiddenException("只有作者可以" + action);
        }
    }

    /** 代號去空白、轉大寫、去重複，而且每一檔都要存在 */
    private Set<String> validStockIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = raw.stream().map(id -> id.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() > MAX_STOCK_TAGS) {
            throw new BadRequestException("最多標記 " + MAX_STOCK_TAGS + " 檔股票");
        }
        Set<String> found = stockNames(ids).keySet();
        List<String> missing = ids.stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("查無股票 " + String.join("、", missing));
        }
        return ids;
    }

    private Map<String, String> stockNames(Collection<String> stockIds) {
        return stockDao.findByIds(stockIds).stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName, (a, b) -> a));
    }

    private static List<StockTag> tags(Collection<String> stockIds, Map<String, StockTag> quotes) {
        return stockIds.stream()
                .map(id -> quotes.getOrDefault(id, new StockTag(id, id, null, null, null, null)))
                .toList();
    }

    /**
     * 每檔股票的名稱與最新漲跌。不管一頁有幾檔，都只用兩句 SQL：一句查名稱、一句查最近的日線。
     */
    private Map<String, StockTag> stockTags(Collection<String> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = stockNames(stockIds);
        Map<String, List<DailyPrice>> pricesByStock = new HashMap<>();
        LocalDate from = LocalDate.now(clock).minusDays(QUOTE_LOOKBACK_DAYS);
        for (DailyPrice price : dailyPriceDao.findByStocksSince(names.keySet(), from)) {
            pricesByStock.computeIfAbsent(price.getStock().getStockId(), id -> new ArrayList<>()).add(price);
        }

        Map<String, StockTag> tags = new HashMap<>();
        names.forEach((id, name) -> tags.put(id, quote(id, name, pricesByStock.getOrDefault(id, List.of()))));
        return tags;
    }

    /** prices 依日期由舊到新；最後一筆是最新收盤，跟倒數第二筆比算漲跌 */
    static StockTag quote(String stockId, String name, List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            return new StockTag(stockId, name, null, null, null, null);
        }
        DailyPrice latest = prices.get(prices.size() - 1);
        BigDecimal close = latest.getClosePrice();
        if (prices.size() < 2) {
            return new StockTag(stockId, name, close, null, null, latest.getTradeDate());
        }
        BigDecimal previous = prices.get(prices.size() - 2).getClosePrice();
        BigDecimal change = close.subtract(previous);
        BigDecimal percent = previous.signum() == 0 ? null
                : change.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
        return new StockTag(stockId, name, close, change, percent, latest.getTradeDate());
    }

    private static boolean isEdited(Post post) {
        return post.getUpdatedAt().isAfter(post.getCreatedAt());
    }

    static String excerpt(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH) + "…";
    }
}
