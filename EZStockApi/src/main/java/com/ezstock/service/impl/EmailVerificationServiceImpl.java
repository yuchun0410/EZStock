package com.ezstock.service.impl;

import com.ezstock.dao.MemberDao;
import com.ezstock.dto.MemberResponse;
import com.ezstock.email.EmailProperties;
import com.ezstock.email.EmailSender;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.exception.TooManyRequestsException;
import com.ezstock.model.Member;
import com.ezstock.security.EmailVerificationTokenStore;
import com.ezstock.security.LogMasks;
import com.ezstock.service.EmailVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationServiceImpl.class);

    static final String SUBJECT = "【EZStock】請驗證你的 Email";
    static final String INVALID_LINK = "驗證連結無效或已過期，請登入後重新寄送驗證信";

    private final MemberDao memberDao;
    private final EmailVerificationTokenStore tokenStore;
    private final EmailSender emailSender;
    private final EmailProperties properties;
    private final Clock clock;

    public EmailVerificationServiceImpl(MemberDao memberDao, EmailVerificationTokenStore tokenStore,
                                        EmailSender emailSender, EmailProperties properties, Clock clock) {
        this.memberDao = memberDao;
        this.tokenStore = tokenStore;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void sendVerification(Member member) {
        if (member.getEmail() == null || member.isEmailVerified()) {
            return;
        }
        String token = tokenStore.issue(member.getId());
        String link = properties.verifyUrl() + "?token=" + token;
        try {
            emailSender.send(member.getEmail(), SUBJECT, body(member.getDisplayName(), link));
            log.info("[Email 驗證] 已寄出驗證信 會員 id={} {}", member.getId(), LogMasks.email(member.getEmail()));
        } catch (RuntimeException e) {
            // 不印 token；會員之後可以從網站重寄
            log.warn("[Email 驗證] 驗證信寄送失敗 會員 id={}：{}", member.getId(), e.getMessage());
        }
    }

    @Override
    public MemberResponse verify(String token) {
        long memberId = tokenStore.consume(token).orElseThrow(() -> {
            log.info("[Email 驗證] 驗證失敗：token 無效、過期或已使用");
            return new BadRequestException(INVALID_LINK);
        });
        Member member = memberDao.findById(memberId).orElseThrow(() -> {
            log.info("[Email 驗證] 驗證失敗：會員 id={} 已不存在", memberId);
            return new BadRequestException(INVALID_LINK);
        });
        member.markEmailVerified(LocalDateTime.now(clock));
        memberDao.save(member);
        log.info("[Email 驗證] 驗證成功 會員 id={}", memberId);
        return MemberResponse.from(member);
    }

    @Override
    public void resend(long memberId) {
        Member member = memberDao.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("查無會員"));
        if (member.getEmail() == null) {
            throw new BadRequestException("這個帳號沒有 Email");
        }
        if (member.isEmailVerified()) {
            throw new BadRequestException("Email 已經驗證過了");
        }
        if (!tokenStore.tryStartCooldown(memberId)) {
            long seconds = tokenStore.cooldownSecondsLeft(memberId);
            log.info("[Email 驗證] 重寄太頻繁 會員 id={}，還要等 {} 秒", memberId, seconds);
            throw new TooManyRequestsException("剛剛才寄過驗證信，請稍等一下再試", seconds);
        }
        sendVerification(member);
    }

    private static String body(String displayName, String link) {
        return displayName + " 你好：\n\n"
                + "感謝你註冊 EZStock！請點下面的連結完成 Email 驗證（24 小時內有效）：\n\n"
                + link + "\n\n"
                + "如果你沒有註冊 EZStock，請忽略這封信。";
    }
}
