package com.ezstock.security;

import com.ezstock.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 連真的 Redis（本機 localhost:6379；CI 有開 Redis service）。
 * 用測試專用的 Email 和文件用的 IP（203.0.113.x 是保留給範例的網段），測完把 key 刪掉。
 */
class LoginAttemptLimiterTest {

    private static final String EMAIL = "limiter-test@example.invalid";
    private static final String IP = "203.0.113.250";
    private static final String OTHER_IP = "203.0.113.251";

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static LoginAttemptLimiter limiter;

    private final List<String> usedEmails = new ArrayList<>(List.of(EMAIL));

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        boolean available;
        try (var connection = factory.getConnection()) {
            available = "PONG".equals(connection.ping());
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "沒有 Redis，略過（本機請執行 brew services start redis）");
        redis = new StringRedisTemplate(factory);
        limiter = new LoginAttemptLimiter(redis);
    }

    @AfterEach
    void cleanUp() {
        usedEmails.forEach(email -> redis.delete(LoginAttemptLimiter.emailKey(email)));
        redis.delete(List.of(LoginAttemptLimiter.IP_PREFIX + IP, LoginAttemptLimiter.IP_PREFIX + OTHER_IP));
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("同一個 Email 錯 5 次就鎖住，換 IP 也一樣；回傳還要等幾秒")
    void lockByEmail() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL - 1; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();

        limiter.recordFailure(EMAIL, IP);

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, OTHER_IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("登入失敗次數太多，請 15 分鐘後再試")
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfterSeconds())
                .satisfies(seconds -> assertThat((Long) seconds).isBetween(1L, 900L));
    }

    @Test
    @DisplayName("Redis 只存 Email 的雜湊，而且有 TTL")
    void storesHashWithTtl() {
        limiter.recordFailure(EMAIL, IP);

        String key = LoginAttemptLimiter.emailKey(EMAIL);
        assertThat(key).doesNotContain("limiter-test");
        assertThat(redis.opsForValue().get(key)).isEqualTo("1");
        assertThat(redis.getExpire(key)).isBetween(1L, 900L);
    }

    @Test
    @DisplayName("登入成功只清 Email 的次數，IP 的次數保留")
    void successResetsEmailOnly() {
        limiter.recordFailure(EMAIL, IP);
        limiter.recordFailure(EMAIL, IP);

        limiter.recordSuccess(EMAIL);

        assertThat(redis.hasKey(LoginAttemptLimiter.emailKey(EMAIL))).isFalse();
        assertThat(redis.opsForValue().get(LoginAttemptLimiter.IP_PREFIX + IP)).isEqualTo("2");
    }

    @Test
    @DisplayName("同一個 IP 換很多 Email 試，錯 20 次後連沒試過的 Email 也擋")
    void lockByIp() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            String email = "limiter-test-" + i + "@example.invalid";
            usedEmails.add(email);
            limiter.recordFailure(email, IP);
        }

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, IP)).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkAllowed(EMAIL, OTHER_IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("沒有 TTL 的 key 不會變成永久鎖住：檢查時補上 TTL")
    void fixesMissingTtl() {
        String key = LoginAttemptLimiter.emailKey(EMAIL);
        redis.opsForValue().set(key, "5");

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, IP)).isInstanceOf(TooManyRequestsException.class);
        assertThat(redis.getExpire(key)).isBetween(1L, 900L);
    }
}
