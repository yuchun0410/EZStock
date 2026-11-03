package com.ezstock.security;

import com.ezstock.exception.TooManyRequestsException;
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
 * 登入失敗次數限制，防止有人一直猜密碼（暴力破解）。
 *
 * 用 Redis 計數，兩種上限同時檢查：
 * - 同一個 Email 15 分鐘內錯 5 次：擋「針對一個帳號猜密碼」
 * - 同一個 IP 15 分鐘內錯 20 次：擋「用同一台機器換很多帳號試」（撞庫）
 *
 * Redis 的 key（Email 先做 SHA-256，Redis 裡不存明碼的 Email）：
 * ezstock:auth:login-fail:email:{hash} → 失敗次數，TTL 15 分鐘
 * ezstock:auth:login-fail:ip:{ip}      → 失敗次數，TTL 15 分鐘
 *
 * 固定時間窗：第一次失敗時開始計時，15 分鐘後 key 自動消失、次數歸零。
 */
@Component
public class LoginAttemptLimiter {

    static final String EMAIL_PREFIX = "ezstock:auth:login-fail:email:";
    static final String IP_PREFIX = "ezstock:auth:login-fail:ip:";
    static final int MAX_FAILURES_PER_EMAIL = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final StringRedisTemplate redis;

    public LoginAttemptLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 驗證密碼「之前」先檢查。鎖住期間就算密碼對也不讓登入，不然攻擊者可以繼續猜，只是看不到結果而已。
     * Redis 出錯時放行（fail open）並記 ERROR：這是額外的保護，不應該因為它讓所有人都不能登入。
     */
    public void checkAllowed(String email, String ip) {
        long waitSeconds;
        try {
            waitSeconds = Math.max(
                    lockedSeconds(emailKey(email), MAX_FAILURES_PER_EMAIL),
                    ip == null ? 0 : lockedSeconds(IP_PREFIX + ip, MAX_FAILURES_PER_IP));
        } catch (RuntimeException e) {
            log.error("[登入限制] 無法檢查失敗次數（Redis 錯誤），先放行：{}", e.getMessage());
            return;
        }
        if (waitSeconds > 0) {
            log.warn("[登入限制] 失敗太多次，拒絕登入 {} IP={}，{} 秒後解除", LogMasks.email(email), ip, waitSeconds);
            long minutes = (waitSeconds + 59) / 60;
            throw new TooManyRequestsException("登入失敗次數太多，請 " + minutes + " 分鐘後再試", waitSeconds);
        }
    }

    public void recordFailure(String email, String ip) {
        try {
            long emailCount = increment(emailKey(email));
            long ipCount = ip == null ? 0 : increment(IP_PREFIX + ip);
            log.info("[登入限制] 登入失敗 {}（{}/{}）IP={}（{}/{}）", LogMasks.email(email), emailCount,
                    MAX_FAILURES_PER_EMAIL, ip, ipCount, MAX_FAILURES_PER_IP);
        } catch (RuntimeException e) {
            log.error("[登入限制] 無法記錄失敗次數（Redis 錯誤）：{}", e.getMessage());
        }
    }

    /**
     * 登入成功只清掉這個 Email 的次數，不清 IP 的：
     * 不然攻擊者只要穿插登入自己的帳號，就能一直把 IP 的次數歸零。
     */
    public void recordSuccess(String email) {
        try {
            redis.delete(emailKey(email));
        } catch (RuntimeException e) {
            log.error("[登入限制] 無法清除失敗次數（Redis 錯誤）：{}", e.getMessage());
        }
    }

    /** 還沒到上限回 0；到了回還要等幾秒 */
    private long lockedSeconds(String key, int max) {
        String value = redis.opsForValue().get(key);
        if (value == null || Long.parseLong(value) < max) {
            return 0;
        }
        Long ttl = redis.getExpire(key);
        if (ttl == null || ttl < 0) {
            // 沒有 TTL 的 key 會變成永久鎖住，補上
            redis.expire(key, WINDOW);
            return WINDOW.toSeconds();
        }
        return Math.max(ttl, 1);
    }

    /**
     * INCR 是原子操作，同時好幾個請求也不會少算。
     * 第一次（結果是 1）才設 TTL，之後再錯不會延長時間；
     * 萬一 INCR 之後還沒設到 TTL 就出錯，下一次會補上，避免留下永遠不過期的 key。
     */
    private long increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return 0;
        }
        if (count == 1) {
            redis.expire(key, WINDOW);
        } else {
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl == -1) {
                redis.expire(key, WINDOW);
            }
        }
        return count;
    }

    static String emailKey(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return EMAIL_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
