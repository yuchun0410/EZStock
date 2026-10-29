package com.ezstock.security;

import com.ezstock.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/**
 * Refresh token 存在 Redis。
 *
 * <ul>
 *   <li>refresh token 是隨機字串，不是 JWT；Redis 只存它的 SHA-256，就算 Redis 被看光也拿不到可用的 token</li>
 *   <li>到期時間交給 Redis 的 TTL，不用寫排程清資料</li>
 *   <li>輪替：每換一次，舊的就作廢，並留下「用過」的記號</li>
 *   <li>重用偵測：有人拿已作廢的舊 token 來換，代表 token 可能被偷了，把這個會員所有登入都踢掉</li>
 * </ul>
 *
 * Redis 的 key：
 * <pre>
 * ezstock:auth:refresh:{hash}       → 會員 id   （有效的 refresh token）
 * ezstock:auth:refresh-used:{hash}  → 會員 id   （已經換過的舊 token，用來偵測重用）
 * ezstock:auth:member-refresh:{id}  → {hash...}（這個會員所有有效的 token，用來一次全部登出）
 * </pre>
 */
@Component
public class RefreshTokenStore {

    static final String ACTIVE_PREFIX = "ezstock:auth:refresh:";
    static final String USED_PREFIX = "ezstock:auth:refresh-used:";
    static final String MEMBER_PREFIX = "ezstock:auth:member-refresh:";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.ttl = properties.refreshTokenTtl();
    }

    /** 發一個新的 refresh token，回傳原始字串（只放進 cookie，不寫 log） */
    public String issue(long memberId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);

        redis.opsForValue().set(ACTIVE_PREFIX + hash, String.valueOf(memberId), ttl);
        redis.opsForSet().add(MEMBER_PREFIX + memberId, hash);
        redis.expire(MEMBER_PREFIX + memberId, ttl);
        log.debug("[Refresh] 發給會員 id={}，token 編號 {}", memberId, shortId(hash));
        return token;
    }

    /**
     * 輪替：舊的作廢、發新的。
     * GETDEL 是一個原子操作，同一個 token 同時送兩次，只有一次會成功。
     *
     * @return 會員 id 與新的 refresh token
     */
    public Rotated rotate(String token) {
        String hash = hash(token);
        String memberId = redis.opsForValue().getAndDelete(ACTIVE_PREFIX + hash);
        if (memberId == null) {
            String reusedBy = redis.opsForValue().get(USED_PREFIX + hash);
            if (reusedBy != null) {
                log.warn("[Refresh] 偵測到已作廢的 token {} 被重複使用，會員 id={} 可能被盜用，登出所有裝置",
                        shortId(hash), reusedBy);
                revokeAll(Long.parseLong(reusedBy));
            } else {
                log.info("[Refresh] token {} 不存在或已過期", shortId(hash));
            }
            throw new UnauthorizedException("登入已過期，請重新登入");
        }
        long id = Long.parseLong(memberId);
        redis.opsForSet().remove(MEMBER_PREFIX + id, hash);
        redis.opsForValue().set(USED_PREFIX + hash, memberId, ttl);
        String next = issue(id);
        log.info("[Refresh] 會員 id={} 換發 refresh token（{} → {}）", id, shortId(hash), shortId(hash(next)));
        return new Rotated(id, next);
    }

    /** 登出這一個裝置 */
    public void revoke(String token) {
        String hash = hash(token);
        String memberId = redis.opsForValue().getAndDelete(ACTIVE_PREFIX + hash);
        if (memberId != null) {
            redis.opsForSet().remove(MEMBER_PREFIX + memberId, hash);
            log.info("[Refresh] 會員 id={} 登出，作廢 token {}", memberId, shortId(hash));
        }
    }

    /** 登出這個會員的所有裝置 */
    public void revokeAll(long memberId) {
        Set<String> hashes = redis.opsForSet().members(MEMBER_PREFIX + memberId);
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(h -> ACTIVE_PREFIX + h).toList());
        }
        redis.delete(MEMBER_PREFIX + memberId);
        log.warn("[Refresh] 會員 id={} 所有裝置已登出，共 {} 個", memberId, hashes == null ? 0 : hashes.size());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** log 只印雜湊的前 8 碼，足夠追蹤同一個 token，又不會洩漏 */
    private static String shortId(String hash) {
        return hash.substring(0, 8);
    }

    public record Rotated(long memberId, String refreshToken) {

        @Override
        public String toString() {
            return "Rotated[memberId=" + memberId + "]";
        }
    }
}
