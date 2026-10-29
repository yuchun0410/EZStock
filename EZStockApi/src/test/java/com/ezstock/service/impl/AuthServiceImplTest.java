package com.ezstock.service.impl;

import com.ezstock.dao.MemberDao;
import com.ezstock.dto.LoginRequest;
import com.ezstock.dto.RegisterRequest;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ConflictException;
import com.ezstock.exception.UnauthorizedException;
import com.ezstock.model.Member;
import com.ezstock.security.AccessToken;
import com.ezstock.security.AccessTokenBlacklist;
import com.ezstock.security.GoogleIdTokenVerifier;
import com.ezstock.security.GoogleUser;
import com.ezstock.security.RefreshTokenStore;
import com.ezstock.security.TokenService;
import com.ezstock.service.AuthService.AuthResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private static final String PASSWORD = "correct-password";
    private static final Instant NOW = Instant.parse("2026-10-29T06:00:00Z");

    /** 強度 4 是 BCrypt 最低，測試跑比較快；正式環境用預設的 10 */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private MemberDao memberDao;
    private TokenService tokenService;
    private RefreshTokenStore refreshTokenStore;
    private AccessTokenBlacklist blacklist;
    private JwtDecoder jwtDecoder;
    private GoogleIdTokenVerifier googleVerifier;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        memberDao = mock(MemberDao.class);
        tokenService = mock(TokenService.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        blacklist = mock(AccessTokenBlacklist.class);
        jwtDecoder = mock(JwtDecoder.class);
        googleVerifier = mock(GoogleIdTokenVerifier.class);
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Taipei"));
        service = new AuthServiceImpl(memberDao, encoder, tokenService, refreshTokenStore, blacklist, jwtDecoder,
                googleVerifier, clock);

        when(memberDao.findByEmail(anyString())).thenReturn(Optional.empty());
        when(memberDao.findByGoogleSub(anyString())).thenReturn(Optional.empty());
        when(memberDao.save(any())).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            if (member.getId() == null) {
                ReflectionTestUtils.setField(member, "id", 1L);
            }
            return member;
        });
        when(tokenService.issueAccessToken(any())).thenReturn(new AccessToken("jwt", "jti-1", NOW.plusSeconds(900)));
        when(refreshTokenStore.issue(anyLong())).thenReturn("refresh-1");
    }

    @Test
    @DisplayName("註冊：Email 轉小寫、密碼存 BCrypt 雜湊，並直接登入")
    void register() {
        AuthResult result = service.register(new RegisterRequest("New@Example.com", PASSWORD, " 小明 "));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberDao).save(saved.capture());
        Member member = saved.getValue();
        assertThat(member.getEmail()).isEqualTo("new@example.com");
        assertThat(member.getDisplayName()).isEqualTo("小明");
        assertThat(member.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$");
        assertThat(encoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
        assertThat(member.getLastLoginAt()).isNotNull();

        assertThat(result.accessToken().value()).isEqualTo("jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        assertThat(result.member().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("註冊：Email 已存在回 409")
    void registerDuplicate() {
        when(memberDao.findByEmail("a@example.com")).thenReturn(Optional.of(member("a@example.com", PASSWORD)));

        assertThatThrownBy(() -> service.register(new RegisterRequest("A@example.com", PASSWORD, "小明")))
                .isInstanceOf(ConflictException.class);
        verify(memberDao, never()).save(any());
    }

    @Test
    @DisplayName("註冊：兩個請求同時註冊，資料庫唯一索引擋下第二個，也回 409")
    void registerRace() {
        // 已經 stub 過的方法要改行為，用 doThrow：when(memberDao.save(any())) 會先真的呼叫一次舊的 stub
        doThrow(new DataIntegrityViolationException("uk_member_email")).when(memberDao).save(any());

        assertThatThrownBy(() -> service.register(new RegisterRequest("a@example.com", PASSWORD, "小明")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("註冊：密碼超過 BCrypt 的 72 byte 上限回 400")
    void registerPasswordTooLong() {
        String chinese = "密".repeat(25); // 25 個中文字 = 75 byte

        assertThatThrownBy(() -> service.register(new RegisterRequest("a@example.com", chinese, "小明")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("登入成功：記錄登入時間，發 access token 和 refresh token")
    void login() {
        Member member = member("a@example.com", PASSWORD);
        when(memberDao.findByEmail("a@example.com")).thenReturn(Optional.of(member));

        AuthResult result = service.login(new LoginRequest(" A@Example.com", PASSWORD));

        assertThat(member.getLastLoginAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Taipei")));
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        verify(refreshTokenStore).issue(7L);
    }

    @Test
    @DisplayName("登入失敗：密碼錯、Email 不存在、只用 Google 登入，都回同一句話")
    void loginFailures() {
        when(memberDao.findByEmail("a@example.com")).thenReturn(Optional.of(member("a@example.com", PASSWORD)));
        Member googleOnly = Member.withGoogle("g@example.com", "sub", "G", null, LocalDateTime.now());
        ReflectionTestUtils.setField(googleOnly, "id", 8L);
        when(memberDao.findByEmail("g@example.com")).thenReturn(Optional.of(googleOnly));

        assertThatThrownBy(() -> service.login(new LoginRequest("a@example.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthServiceImpl.LOGIN_FAILED);
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthServiceImpl.LOGIN_FAILED);
        assertThatThrownBy(() -> service.login(new LoginRequest("g@example.com", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthServiceImpl.LOGIN_FAILED);
        verify(refreshTokenStore, never()).issue(anyLong());
    }

    @Test
    @DisplayName("換 token：refresh token 輪替，發新的 access token")
    void refresh() {
        when(refreshTokenStore.rotate("old")).thenReturn(new RefreshTokenStore.Rotated(7L, "new"));
        when(memberDao.findById(7L)).thenReturn(Optional.of(member("a@example.com", PASSWORD)));

        AuthResult result = service.refresh("old");

        assertThat(result.refreshToken()).isEqualTo("new");
        assertThat(result.accessToken().value()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("換 token：沒有 cookie 回 401；會員已被刪除就登出所有裝置")
    void refreshFailures() {
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(UnauthorizedException.class);

        when(refreshTokenStore.rotate("old")).thenReturn(new RefreshTokenStore.Rotated(99L, "new"));
        when(memberDao.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenStore).revokeAll(99L);
    }

    @Test
    @DisplayName("登出：refresh token 作廢，access token 放進黑名單直到原本的到期時間")
    void logout() {
        Jwt jwt = Jwt.withTokenValue("access").header("alg", "RS256").jti("jti-9")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(600)).build();
        when(jwtDecoder.decode("access")).thenReturn(jwt);

        service.logout("refresh", "access");

        verify(refreshTokenStore).revoke("refresh");
        verify(blacklist).revoke("jti-9", NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("登出：access token 已過期也能登出，不放黑名單")
    void logoutWithExpiredToken() {
        when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("expired"));
        doAnswer(invocation -> null).when(refreshTokenStore).revoke(anyString());

        service.logout("refresh", "expired");

        verify(refreshTokenStore).revoke("refresh");
        verify(blacklist, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("Google 登入：第一次登入，建立沒有密碼的新會員，名稱與頭像用 Google 的")
    void googleNewMember() {
        when(googleVerifier.verify("id-token"))
                .thenReturn(new GoogleUser("g-sub", "G.User@gmail.com", true, "Google 小明", "https://pic"));

        AuthResult result = service.loginWithGoogle("id-token");

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberDao).save(saved.capture());
        Member member = saved.getValue();
        assertThat(member.getEmail()).isEqualTo("g.user@gmail.com");
        assertThat(member.getGoogleSub()).isEqualTo("g-sub");
        assertThat(member.getDisplayName()).isEqualTo("Google 小明");
        assertThat(member.getAvatarUrl()).isEqualTo("https://pic");
        assertThat(member.hasPassword()).isFalse();
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
    }

    @Test
    @DisplayName("Google 登入：同 Email 的既有會員，綁定 Google 帳號，密碼保留")
    void googleLinksExistingMember() {
        Member existing = member("a@example.com", PASSWORD);
        when(memberDao.findByEmail("a@example.com")).thenReturn(Optional.of(existing));
        when(googleVerifier.verify("id-token"))
                .thenReturn(new GoogleUser("g-sub", "a@example.com", true, "小明", "https://pic"));

        service.loginWithGoogle("id-token");

        assertThat(existing.getGoogleSub()).isEqualTo("g-sub");
        assertThat(existing.hasPassword()).isTrue();
        verify(memberDao).save(existing);
        verify(refreshTokenStore).issue(7L);
    }

    @Test
    @DisplayName("Google 登入：已經綁定過，用 Google 帳號編號找到會員（就算 Google 的 Email 改了）")
    void googleExistingLink() {
        Member linked = Member.withGoogle("old@gmail.com", "g-sub", "小明", null, LocalDateTime.now());
        ReflectionTestUtils.setField(linked, "id", 9L);
        when(memberDao.findByGoogleSub("g-sub")).thenReturn(Optional.of(linked));
        when(googleVerifier.verify("id-token"))
                .thenReturn(new GoogleUser("g-sub", "new@gmail.com", true, "小明", null));

        service.loginWithGoogle("id-token");

        verify(refreshTokenStore).issue(9L);
        verify(memberDao, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Google 登入：Email 沒經 Google 驗證不能登入；同 Email 已綁其他 Google 帳號回 409")
    void googleRejected() {
        when(googleVerifier.verify("unverified"))
                .thenReturn(new GoogleUser("g-1", "a@example.com", false, "小明", null));
        assertThatThrownBy(() -> service.loginWithGoogle("unverified")).isInstanceOf(UnauthorizedException.class);

        Member other = Member.withGoogle("a@example.com", "g-other", "小明", null, LocalDateTime.now());
        ReflectionTestUtils.setField(other, "id", 10L);
        when(memberDao.findByEmail("a@example.com")).thenReturn(Optional.of(other));
        when(googleVerifier.verify("id-token"))
                .thenReturn(new GoogleUser("g-2", "a@example.com", true, "小明", null));
        assertThatThrownBy(() -> service.loginWithGoogle("id-token")).isInstanceOf(ConflictException.class);

        verify(refreshTokenStore, never()).issue(anyLong());
    }

    private Member member(String email, String password) {
        Member member = Member.withPassword(email, encoder.encode(password), "小明", LocalDateTime.now());
        ReflectionTestUtils.setField(member, "id", 7L);
        return member;
    }
}
