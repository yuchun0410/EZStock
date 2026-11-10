package com.ezstock.service.impl;

import com.ezstock.model.Post;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 熱門文章的熱度分數，參考 Hacker News 的排序方式：
 * <pre>
 *   分數 = (讚 × 3 + 留言 × 2 + 瀏覽 × 0.1) ÷ (發文到現在幾小時 + 2) ^ gravity
 * </pre>
 * <ul>
 *   <li>分子是互動量：按讚、留言比只是看過更能代表「大家覺得有料」，權重比較高</li>
 *   <li>分母隨時間變大：同樣的互動量，越新的文章分數越高，舊文章不會一直霸佔熱門</li>
 *   <li>gravity 是時間衰減的強度，依網站流量調整（見 ForumProperties）</li>
 *   <li>分母 +2：剛發文時時間接近 0，避免分數暴衝</li>
 *   <li>分子不加基本分：完全沒有互動的文章是 0 分，排在有互動的文章後面（同分再依發文時間，新的在前）。
 *       一開始有加 +1，結果剛發、沒人理的文章會贏過幾天前很多人按讚的文章，所以拿掉了</li>
 * </ul>
 */
final class HotScore {

    static final double LIKE_WEIGHT = 3;
    static final double COMMENT_WEIGHT = 2;
    static final double VIEW_WEIGHT = 0.1;

    private HotScore() {
    }

    static double of(Post post, LocalDateTime now, double gravity) {
        return of(post.getLikeCount(), post.getCommentCount(), post.getViewCount(), post.getCreatedAt(), now, gravity);
    }

    static double of(int likes, int comments, int views, LocalDateTime createdAt, LocalDateTime now, double gravity) {
        double interactions = likes * LIKE_WEIGHT + comments * COMMENT_WEIGHT + views * VIEW_WEIGHT;
        double hours = Math.max(0, Duration.between(createdAt, now).toMinutes() / 60.0);
        return interactions / Math.pow(hours + 2, gravity);
    }
}
