package com.ezstock.line;

import com.ezstock.dao.MemberDao;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

/**
 * 網站帳號與 LINE 帳號的綁定。
 *
 * 流程：網站產生 6 位數綁定碼（存 Redis，10 分鐘過期）→ 使用者在 LINE 傳「綁定 123456」→ 綁定成功。
 * 綁定碼只能用一次（GETDEL）；同一個會員重新產生時，舊的綁定碼作廢。
 * 6 位數只有 100 萬種，為了防止有人在 LINE 一直亂猜，同一個 LINE 帳號 10 分鐘內最多猜錯 5 次。
 *
 * Redis 的 key：
 * <pre>
 * ezstock:line:bind-code:{綁定碼}       → 會員 id
 * ezstock:line:bind-member:{會員 id}    → 綁定碼（用來作廢舊的）
 * ezstock:line:bind-fail:{LINE 使用者}  → 猜錯次數
 * </pre>
 */
@Service
public class LineBindingService {

    static final String CODE_PREFIX = "ezstock:line:bind-code:";
    static final String MEMBER_PREFIX = "ezstock:line:bind-member:";
    static final String FAIL_PREFIX = "ezstock:line:bind-fail:";
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_FAILURES = 5;

    private static final Logger log = LoggerFactory.getLogger(LineBindingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final MemberDao memberDao;

    public LineBindingService(StringRedisTemplate redis, MemberDao memberDao) {
        this.redis = redis;
        this.memberDao = memberDao;
    }

    public record BindingCode(String code, long expiresInSeconds) {
    }

    /** 綁定的結果，給 Bot 回覆用 */
    public sealed interface BindResult {
        record Success(Member member) implements BindResult { }
        record WrongCode(int remainingTries) implements BindResult { }
        record TooManyFailures() implements BindResult { }
    }

    /** 產生新的綁定碼；同一個會員之前的綁定碼會作廢 */
    public BindingCode createCode(long memberId) {
        String old = redis.opsForValue().getAndDelete(MEMBER_PREFIX + memberId);
        if (old != null) {
            redis.delete(CODE_PREFIX + old);
        }
        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
            // setIfAbsent（SETNX）：萬一跟別人還沒過期的綁定碼撞到，就重新產生一個
        } while (!Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(CODE_PREFIX + code, String.valueOf(memberId), CODE_TTL)));
        redis.opsForValue().set(MEMBER_PREFIX + memberId, code, CODE_TTL);
        log.info("[LINE 綁定] 會員 id={} 產生綁定碼，{} 分鐘內有效", memberId, CODE_TTL.toMinutes());
        return new BindingCode(code, CODE_TTL.toSeconds());
    }

    /**
     * 用 LINE 傳來的綁定碼完成綁定。
     * 不放在同一個交易：先解除舊會員的綁定、再綁新的，各自存檔，才不會撞到 line_user_id 的唯一索引。
     */
    public BindResult bind(String lineUserId, String code) {
        String failKey = FAIL_PREFIX + lineUserId;
        String failures = redis.opsForValue().get(failKey);
        if (failures != null && Integer.parseInt(failures) >= MAX_FAILURES) {
            log.warn("[LINE 綁定] 使用者 {} 猜錯太多次，暫時鎖住", LineMessagingClient.mask(lineUserId));
            return new BindResult.TooManyFailures();
        }

        String memberId = redis.opsForValue().getAndDelete(CODE_PREFIX + code);
        Optional<Member> member = memberId == null ? Optional.empty() : memberDao.findById(Long.parseLong(memberId));
        if (member.isEmpty()) {
            Long count = redis.opsForValue().increment(failKey);
            redis.expire(failKey, CODE_TTL);
            int remaining = MAX_FAILURES - (count == null ? MAX_FAILURES : count.intValue());
            log.info("[LINE 綁定] 使用者 {} 綁定碼錯誤或過期，還可以試 {} 次",
                    LineMessagingClient.mask(lineUserId), Math.max(remaining, 0));
            return remaining > 0 ? new BindResult.WrongCode(remaining) : new BindResult.TooManyFailures();
        }
        redis.delete(MEMBER_PREFIX + memberId);
        redis.delete(failKey);

        // 這個 LINE 帳號之前綁過別的會員，先解除
        memberDao.findByLineUserId(lineUserId)
                .filter(previous -> !previous.getId().equals(member.get().getId()))
                .ifPresent(previous -> {
                    previous.unlinkLine();
                    memberDao.save(previous);
                    log.info("[LINE 綁定] 使用者 {} 原本綁定會員 id={}，已解除",
                            LineMessagingClient.mask(lineUserId), previous.getId());
                });

        Member target = member.get();
        target.linkLine(lineUserId);
        memberDao.save(target);
        log.info("[LINE 綁定] 會員 id={} 綁定 LINE 使用者 {}", target.getId(), LineMessagingClient.mask(lineUserId));
        return new BindResult.Success(target);
    }

    public boolean isLinked(long memberId) {
        return memberDao.findById(memberId).map(m -> m.getLineUserId() != null).orElse(false);
    }

    public void unlink(long memberId) {
        Member member = memberDao.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("查無會員"));
        if (member.getLineUserId() != null) {
            member.unlinkLine();
            memberDao.save(member);
            log.info("[LINE 綁定] 會員 id={} 解除 LINE 綁定", memberId);
        }
    }

    /** 從 LINE 傳「解除綁定」 */
    public Optional<Member> unlinkByLineUser(String lineUserId) {
        Optional<Member> member = memberDao.findByLineUserId(lineUserId);
        member.ifPresent(m -> unlink(m.getId()));
        return member;
    }
}
