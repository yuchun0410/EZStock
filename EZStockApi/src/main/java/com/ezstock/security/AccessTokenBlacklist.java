package com.ezstock.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 登出時，把還沒過期的 access token 編號（jti）放進 Redis 黑名單。
 * JWT 發出去之後伺服器收不回來，黑名單就是用來補這個洞；
 * 只需要存到 token 原本的到期時間，之後 token 本身就失效了，Redis 會自動刪掉這筆。
 */
@Component
public class AccessTokenBlacklist {

    static final String KEY_PREFIX = "ezstock:auth:blacklist:";

    private static final Logger log = LoggerFactory.getLogger(AccessTokenBlacklist.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public AccessTokenBlacklist(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (jti == null || remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", remaining);
        log.info("[JWT] access token 加入黑名單 jti={}，{} 秒後自動移除", jti, remaining.toSeconds());
    }

    /**
     * Redis 連不上時當作「已撤銷」（fail closed）：寧可讓使用者重新登入，也不放行可能已登出的 token。
     */
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
        } catch (RuntimeException e) {
            log.error("[JWT] 無法檢查黑名單（Redis 錯誤），先拒絕這個 token：{}", e.getMessage());
            return true;
        }
    }
}
