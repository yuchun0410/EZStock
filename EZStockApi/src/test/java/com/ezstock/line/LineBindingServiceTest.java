package com.ezstock.line;

import com.ezstock.dao.MemberDao;
import com.ezstock.model.Member;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 連真的 Redis（本機 localhost:6379；CI 有開 Redis service），用不可能出現的會員 id 與 LINE 使用者 id。
 */
class LineBindingServiceTest {

    private static final long MEMBER_ID = 987654321L;
    private static final String LINE_USER = "U-test-binding-000000";

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    private MemberDao memberDao;
    private Member member;
    private LineBindingService service;

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
    }

    @BeforeEach
    void setUp() {
        memberDao = mock(MemberDao.class);
        member = Member.withPassword("line@example.com", "hash", "小明", LocalDateTime.now());
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberDao.findById(anyLong())).thenReturn(Optional.empty());
        when(memberDao.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberDao.findByLineUserId(anyString())).thenReturn(Optional.empty());
        service = new LineBindingService(redis, memberDao);
    }

    @AfterEach
    void cleanUp() {
        String code = redis.opsForValue().getAndDelete(LineBindingService.MEMBER_PREFIX + MEMBER_ID);
        if (code != null) {
            redis.delete(LineBindingService.CODE_PREFIX + code);
        }
        redis.delete(LineBindingService.FAIL_PREFIX + LINE_USER);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("綁定碼：6 位數、10 分鐘過期，用一次就失效")
    void bindOnce() {
        String code = service.createCode(MEMBER_ID).code();
        assertThat(code).matches("\\d{6}");
        assertThat(redis.getExpire(LineBindingService.CODE_PREFIX + code)).isBetween(1L, 600L);

        assertThat(service.bind(LINE_USER, code)).isInstanceOf(LineBindingService.BindResult.Success.class);
        assertThat(member.getLineUserId()).isEqualTo(LINE_USER);
        verify(memberDao).save(member);

        assertThat(service.bind(LINE_USER, code)).isInstanceOf(LineBindingService.BindResult.WrongCode.class);
    }

    @Test
    @DisplayName("重新產生綁定碼：舊的作廢")
    void regenerate() {
        String first = service.createCode(MEMBER_ID).code();
        String second = service.createCode(MEMBER_ID).code();

        assertThat(redis.hasKey(LineBindingService.CODE_PREFIX + first)).isEqualTo(first.equals(second));
        assertThat(service.bind(LINE_USER, second)).isInstanceOf(LineBindingService.BindResult.Success.class);
    }

    @Test
    @DisplayName("猜錯 5 次就鎖住，鎖住後就算拿到正確的碼也不行")
    void tooManyFailures() {
        String code = service.createCode(MEMBER_ID).code();
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < LineBindingService.MAX_FAILURES - 1; i++) {
            assertThat(service.bind(LINE_USER, wrong)).isInstanceOf(LineBindingService.BindResult.WrongCode.class);
        }
        assertThat(service.bind(LINE_USER, wrong)).isInstanceOf(LineBindingService.BindResult.TooManyFailures.class);
        assertThat(service.bind(LINE_USER, code)).isInstanceOf(LineBindingService.BindResult.TooManyFailures.class);
        assertThat(member.getLineUserId()).isNull();
    }

    @Test
    @DisplayName("這個 LINE 帳號原本綁別的會員：先解除舊的再綁新的")
    void rebind() {
        Member previous = Member.withPassword("old@example.com", "hash", "舊帳號", LocalDateTime.now());
        ReflectionTestUtils.setField(previous, "id", MEMBER_ID + 1);
        previous.linkLine(LINE_USER);
        when(memberDao.findByLineUserId(LINE_USER)).thenReturn(Optional.of(previous));

        service.bind(LINE_USER, service.createCode(MEMBER_ID).code());

        assertThat(previous.getLineUserId()).isNull();
        assertThat(member.getLineUserId()).isEqualTo(LINE_USER);
        verify(memberDao).save(previous);
        verify(memberDao).save(member);
    }
}
