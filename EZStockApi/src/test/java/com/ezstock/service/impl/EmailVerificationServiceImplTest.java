package com.ezstock.service.impl;

import com.ezstock.dao.MemberDao;
import com.ezstock.dto.MemberResponse;
import com.ezstock.email.EmailProperties;
import com.ezstock.email.EmailSender;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.TooManyRequestsException;
import com.ezstock.model.Member;
import com.ezstock.security.EmailVerificationTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-16T06:00:00Z");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 16, 13, 0);

    private MemberDao memberDao;
    private EmailVerificationTokenStore tokenStore;
    private EmailSender emailSender;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        memberDao = mock(MemberDao.class);
        tokenStore = mock(EmailVerificationTokenStore.class);
        emailSender = mock(EmailSender.class);
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Taipei"));
        service = new EmailVerificationServiceImpl(memberDao, tokenStore, emailSender,
                new EmailProperties("https://ezstock.example/verify-email"), clock);
        when(tokenStore.issue(anyLong())).thenReturn("token-123");
    }

    @Test
    @DisplayName("寄驗證信：信裡有前端驗證頁的連結和 token")
    void sendVerification() {
        service.sendVerification(member("a@example.com"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("a@example.com"), eq(EmailVerificationServiceImpl.SUBJECT), text.capture());
        assertThat(text.getValue()).contains("小明", "https://ezstock.example/verify-email?token=token-123");
    }

    @Test
    @DisplayName("寄驗證信：沒有 Email、或已經驗證過，就不寄")
    void sendVerificationSkipped() {
        Member verified = member("a@example.com");
        verified.markEmailVerified(CREATED);
        service.sendVerification(verified);
        service.sendVerification(Member.withLine("U-1", null, "LINE 會員", null, CREATED));

        verify(tokenStore, never()).issue(anyLong());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("寄驗證信：寄信服務出錯不往外丟，註冊不會因此失敗")
    void sendVerificationFailureIsSwallowed() {
        doThrow(new IllegalStateException("寄信服務掛了")).when(emailSender).send(anyString(), anyString(), anyString());

        assertThatCode(() -> service.sendVerification(member("a@example.com"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("驗證：token 正確就記下驗證時間並存檔")
    void verify_() {
        Member member = member("a@example.com");
        when(tokenStore.consume("token-123")).thenReturn(Optional.of(7L));
        when(memberDao.findById(7L)).thenReturn(Optional.of(member));

        MemberResponse response = service.verify("token-123");

        assertThat(response.emailVerified()).isTrue();
        assertThat(member.getEmailVerifiedAt()).isEqualTo(LocalDateTime.of(2026, 11, 16, 14, 0));
        verify(memberDao).save(member);
    }

    @Test
    @DisplayName("驗證：token 無效、過期、用過，或會員已被刪除，都回 400")
    void verifyInvalid() {
        when(tokenStore.consume("expired")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("expired")).isInstanceOf(BadRequestException.class);

        when(tokenStore.consume("orphan")).thenReturn(Optional.of(99L));
        when(memberDao.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("orphan")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("重寄：沒在冷卻中就寄出")
    void resend() {
        when(memberDao.findById(7L)).thenReturn(Optional.of(member("a@example.com")));
        when(tokenStore.tryStartCooldown(7L)).thenReturn(true);

        service.resend(7L);

        verify(emailSender).send(eq("a@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("重寄：60 秒內再要求回 429，並告訴前端還要等幾秒")
    void resendTooSoon() {
        when(memberDao.findById(7L)).thenReturn(Optional.of(member("a@example.com")));
        when(tokenStore.tryStartCooldown(7L)).thenReturn(false);
        when(tokenStore.cooldownSecondsLeft(7L)).thenReturn(42L);

        assertThatThrownBy(() -> service.resend(7L))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(42L);
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("重寄：已經驗證過、或沒有 Email，回 400")
    void resendNotNeeded() {
        Member verified = member("a@example.com");
        verified.markEmailVerified(CREATED);
        when(memberDao.findById(7L)).thenReturn(Optional.of(verified));
        assertThatThrownBy(() -> service.resend(7L)).isInstanceOf(BadRequestException.class);

        Member noEmail = Member.withLine("U-1", null, "LINE 會員", null, CREATED);
        when(memberDao.findById(8L)).thenReturn(Optional.of(noEmail));
        assertThatThrownBy(() -> service.resend(8L)).isInstanceOf(BadRequestException.class);

        verify(tokenStore, never()).tryStartCooldown(anyLong());
    }

    private static Member member(String email) {
        Member member = Member.withPassword(email, "$2a$04$hash", "小明", CREATED);
        ReflectionTestUtils.setField(member, "id", 7L);
        return member;
    }
}
