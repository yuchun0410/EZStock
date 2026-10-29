package com.ezstock.security;

import com.ezstock.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 連真的 Redis（本機 localhost:6379；CI 有開 Redis service）。
 * 用一個不可能出現的會員 id，測完把相關的 key 刪掉，不影響開發中的資料。
 */
class RefreshTokenStoreTest {

    private static final long MEMBER_ID = 987654321L;

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static RefreshTokenStore store;

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
        store = new RefreshTokenStore(redis, new JwtProperties(null, null, null, Duration.ofMinutes(5), false));
    }

    @AfterEach
    void cleanUp() {
        store.revokeAll(MEMBER_ID);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("Redis 只存 token 的雜湊，而且有 TTL")
    void storesHashWithTtl() {
        String token = store.issue(MEMBER_ID);

        assertThat(redis.hasKey(RefreshTokenStore.ACTIVE_PREFIX + token)).isFalse();
        String key = RefreshTokenStore.ACTIVE_PREFIX + RefreshTokenStore.hash(token);
        assertThat(redis.opsForValue().get(key)).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(redis.getExpire(key)).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("輪替：拿到新的 token，舊的不能再用")
    void rotate() {
        String first = store.issue(MEMBER_ID);

        RefreshTokenStore.Rotated rotated = store.rotate(first);

        assertThat(rotated.memberId()).isEqualTo(MEMBER_ID);
        assertThat(rotated.refreshToken()).isNotEqualTo(first);
        assertThat(store.rotate(rotated.refreshToken()).memberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("舊 token 被重複使用：判斷為被盜用，這個會員所有裝置都登出")
    void reuseRevokesAll() {
        String stolen = store.issue(MEMBER_ID);
        String otherDevice = store.issue(MEMBER_ID);
        String current = store.rotate(stolen).refreshToken();

        assertThatThrownBy(() -> store.rotate(stolen)).isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> store.rotate(current)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> store.rotate(otherDevice)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("登出這個裝置：這個 token 失效，其他裝置不受影響")
    void revokeOne() {
        String phone = store.issue(MEMBER_ID);
        String laptop = store.issue(MEMBER_ID);

        store.revoke(phone);

        assertThatThrownBy(() -> store.rotate(phone)).isInstanceOf(UnauthorizedException.class);
        assertThat(store.rotate(laptop).memberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("亂打的 token：401")
    void unknown() {
        assertThatThrownBy(() -> store.rotate("not-a-real-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("重新登入");
    }
}
