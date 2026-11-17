package com.ezstock.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 連真的 Redis（本機 localhost:6379；CI 有開 Redis service）。
 * 用一個不可能出現的會員 id，測完把冷卻時間的 key 刪掉，不影響開發中的資料。
 */
class EmailVerificationTokenStoreTest {

    private static final long MEMBER_ID = 987654322L;

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static EmailVerificationTokenStore store;

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
        store = new EmailVerificationTokenStore(redis);
    }

    @AfterEach
    void cleanUp() {
        store.clearCooldown(MEMBER_ID);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("token：Redis 只存雜湊、有 24 小時的 TTL，取出一次就失效")
    void tokenIsSingleUse() {
        String token = store.issue(MEMBER_ID);

        String key = EmailVerificationTokenStore.TOKEN_PREFIX + RefreshTokenStore.hash(token);
        assertThat(redis.hasKey(EmailVerificationTokenStore.TOKEN_PREFIX + token)).as("不能存原始 token").isFalse();
        assertThat(redis.opsForValue().get(key)).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(redis.getExpire(key)).isBetween(1L, EmailVerificationTokenStore.TTL.toSeconds());

        assertThat(store.consume(token)).contains(MEMBER_ID);
        assertThat(store.consume(token)).as("第二次就沒了").isEmpty();
    }

    @Test
    @DisplayName("token：亂打的、空白的都拿不到會員")
    void unknownToken() {
        assertThat(store.consume("not-a-real-token")).isEmpty();
        assertThat(store.consume(" ")).isEmpty();
        assertThat(store.consume(null)).isEmpty();
    }

    @Test
    @DisplayName("重寄冷卻：第一次可以寄，冷卻中不行，並知道還剩幾秒")
    void cooldown() {
        assertThat(store.tryStartCooldown(MEMBER_ID)).isTrue();
        assertThat(store.tryStartCooldown(MEMBER_ID)).isFalse();
        assertThat(store.cooldownSecondsLeft(MEMBER_ID))
                .isBetween(1L, EmailVerificationTokenStore.RESEND_COOLDOWN.toSeconds());

        store.clearCooldown(MEMBER_ID);
        assertThat(store.cooldownSecondsLeft(MEMBER_ID)).isZero();
    }
}
