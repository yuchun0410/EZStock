package com.ezstock.service.impl;

import com.ezstock.service.PostViewTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 用 Redis 記「誰在 30 分鐘內看過哪篇」，防止一直重新整理灌瀏覽數。
 * key：ezstock:forum:view:{文章 id}:{看的人}，用 SET NX（不存在才寫入）加 30 分鐘 TTL，
 * 寫入成功就是第一次看；時間到 key 自動消失，之後再看會再算一次。
 * 看的人先做 SHA-256，Redis 裡不存 IP 原文。
 * Redis 出錯時不算這次瀏覽（瀏覽數少算一點沒關係，不能讓文章打不開）。
 */
@Component
public class RedisPostViewTracker implements PostViewTracker {

    private static final Logger log = LoggerFactory.getLogger(RedisPostViewTracker.class);

    static final String KEY_PREFIX = "ezstock:forum:view:";
    static final Duration WINDOW = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public RedisPostViewTracker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isFirstView(long postId, String viewerKey) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(KEY_PREFIX + postId + ":" + sha256(viewerKey), "1", WINDOW);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("[論壇] 瀏覽數記錄失敗（Redis），這次不算：{}", e.getMessage());
            return false;
        }
    }

    static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
