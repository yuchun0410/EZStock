package com.ezstock.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * LINE 登入的 state 和 nonce。
 * - state：防 CSRF。導去 LINE 之前產生，LINE 會原封不動帶回來；同一個值也放在瀏覽器的 HttpOnly cookie，
 *   回來時兩邊對得上，才代表是「這個瀏覽器」剛剛發起的登入，不是別人塞過來的連結
 * - nonce：防重放。LINE 會把它寫進 ID Token，驗證時一定要一樣，偷到舊的 ID Token 也不能重複使用
 *
 * Redis 的 key：ezstock:auth:line-login:{state} → nonce，10 分鐘後自動刪除；用 GETDEL 取出，一個 state 只能用一次。
 */
@Component
public class LineLoginStateStore {

    static final String KEY_PREFIX = "ezstock:auth:line-login:";
    public static final Duration TTL = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public LineLoginStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Pending(String state, String nonce) {
    }

    public Pending create() {
        Pending pending = new Pending(randomToken(), randomToken());
        redis.opsForValue().set(KEY_PREFIX + pending.state(), pending.nonce(), TTL);
        return pending;
    }

    /** 取出並刪除；過期、用過、或根本不是我們發的 state 都回 empty */
    public Optional<String> consumeNonce(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + state));
    }

    /** 32 bytes 隨機數，用網址安全的 Base64（只有英數和 -_） */
    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
