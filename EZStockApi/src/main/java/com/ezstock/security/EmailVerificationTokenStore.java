package com.ezstock.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Email 驗證信裡的 token，存在 Redis。
 *
 * - ezstock:auth:email-verify:{token 的 SHA-256} → 會員 id，24 小時後自動刪除。
 *   只存雜湊：就算 Redis 的資料外洩，也拿不到能用的驗證連結（跟 refresh token 同樣的做法）。
 *   用 GETDEL 取出，一個 token 只能用一次。
 * - ezstock:auth:email-verify-cooldown:{會員 id}：重寄驗證信的冷卻時間，60 秒內只能寄一次，避免被拿來灌信。
 */
@Component
public class EmailVerificationTokenStore {

    static final String TOKEN_PREFIX = "ezstock:auth:email-verify:";
    static final String COOLDOWN_PREFIX = "ezstock:auth:email-verify-cooldown:";
    public static final Duration TTL = Duration.ofHours(24);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public EmailVerificationTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 產生新的驗證 token；回傳的是原始 token（放進信裡），Redis 只存雜湊 */
    public String issue(long memberId) {
        String token = LineLoginStateStore.randomToken();
        redis.opsForValue().set(TOKEN_PREFIX + RefreshTokenStore.hash(token), String.valueOf(memberId), TTL);
        return token;
    }

    /** 取出並刪除；過期、用過、或根本不是我們發的 token 都回 empty */
    public Optional<Long> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String memberId = redis.opsForValue().getAndDelete(TOKEN_PREFIX + RefreshTokenStore.hash(token));
        return Optional.ofNullable(memberId).map(Long::valueOf);
    }

    /**
     * 開始一段重寄的冷卻時間。
     *
     * @return true 代表可以寄；false 代表冷卻中，還不能再寄
     */
    public boolean tryStartCooldown(long memberId) {
        Boolean started = redis.opsForValue().setIfAbsent(COOLDOWN_PREFIX + memberId, "1", RESEND_COOLDOWN);
        return Boolean.TRUE.equals(started);
    }

    /** 冷卻還剩幾秒（給 Retry-After 用）；沒有在冷卻回 0 */
    public long cooldownSecondsLeft(long memberId) {
        Long seconds = redis.getExpire(COOLDOWN_PREFIX + memberId);
        return seconds == null || seconds < 0 ? 0 : seconds;
    }

    /** 測試用：清掉這個會員的冷卻時間 */
    void clearCooldown(long memberId) {
        redis.delete(COOLDOWN_PREFIX + memberId);
    }
}
