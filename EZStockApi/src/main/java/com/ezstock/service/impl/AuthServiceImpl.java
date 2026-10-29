package com.ezstock.service.impl;

import com.ezstock.dao.MemberDao;
import com.ezstock.dto.AuthProvidersResponse;
import com.ezstock.dto.LoginRequest;
import com.ezstock.dto.MemberResponse;
import com.ezstock.dto.RegisterRequest;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ConflictException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.exception.UnauthorizedException;
import com.ezstock.model.Member;
import com.ezstock.security.AccessToken;
import com.ezstock.security.AccessTokenBlacklist;
import com.ezstock.security.GoogleIdTokenVerifier;
import com.ezstock.security.GoogleUser;
import com.ezstock.security.LogMasks;
import com.ezstock.security.RefreshTokenStore;
import com.ezstock.security.TokenService;
import com.ezstock.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 帳號或密碼錯，都回同一句話，不讓別人用登入功能試出哪些 Email 有註冊 */
    static final String LOGIN_FAILED = "Email 或密碼錯誤";

    /** BCrypt 只看前 72 個 byte，超過的部分會被忽略（中文一個字 3 byte） */
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final int DISPLAY_NAME_MAX = 50;

    private final MemberDao memberDao;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlacklist blacklist;
    private final JwtDecoder jwtDecoder;
    private final GoogleIdTokenVerifier googleVerifier;
    private final Clock clock;

    /** Email 不存在時也跑一次 BCrypt，讓回應時間跟「密碼錯」差不多，避免用時間差猜出帳號存不存在 */
    private final String dummyHash;

    public AuthServiceImpl(MemberDao memberDao, PasswordEncoder passwordEncoder, TokenService tokenService,
                           RefreshTokenStore refreshTokenStore, AccessTokenBlacklist blacklist,
                           JwtDecoder jwtDecoder, GoogleIdTokenVerifier googleVerifier, Clock clock) {
        this.memberDao = memberDao;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.blacklist = blacklist;
        this.jwtDecoder = jwtDecoder;
        this.googleVerifier = googleVerifier;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public AuthResult register(RegisterRequest request) {
        String email = Member.normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException("密碼太長，請縮短一點");
        }
        if (memberDao.findByEmail(email).isPresent()) {
            log.info("[會員] 註冊失敗：{} 已經註冊過", LogMasks.email(email));
            throw new ConflictException("這個 Email 已經註冊過，請直接登入");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Member member = Member.withPassword(email, passwordEncoder.encode(request.password()),
                request.displayName().trim(), now);
        member.recordLogin(now);
        try {
            memberDao.save(member);
        } catch (DataIntegrityViolationException e) {
            // 兩個請求同時用同一個 Email 註冊，資料庫的唯一索引會擋下第二個
            log.info("[會員] 註冊失敗：{} 同時有另一個註冊請求", LogMasks.email(email));
            throw new ConflictException("這個 Email 已經註冊過，請直接登入");
        }
        log.info("[會員] 註冊成功 id={} {}", member.getId(), LogMasks.email(email));
        return issueTokens(member);
    }

    @Override
    public AuthResult login(LoginRequest request) {
        String email = Member.normalizeEmail(request.email());
        Member member = memberDao.findByEmail(email).orElse(null);

        if (member == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            log.info("[會員] 登入失敗：{} 沒有註冊", LogMasks.email(email));
            throw new UnauthorizedException(LOGIN_FAILED);
        }
        if (!member.hasPassword()) {
            passwordEncoder.matches(request.password(), dummyHash);
            log.info("[會員] 登入失敗：會員 id={} 沒有設定密碼（只用 Google 登入）", member.getId());
            throw new UnauthorizedException(LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            log.info("[會員] 登入失敗：會員 id={} 密碼錯誤", member.getId());
            throw new UnauthorizedException(LOGIN_FAILED);
        }

        member.recordLogin(LocalDateTime.now(clock));
        memberDao.save(member);
        log.info("[會員] 登入成功 id={}", member.getId());
        return issueTokens(member);
    }

    @Override
    public AuthResult loginWithGoogle(String credential) {
        GoogleUser google = googleVerifier.verify(credential);
        if (google.email() == null || !google.emailVerified()) {
            log.info("[會員] Google 登入失敗：Email 尚未經 Google 驗證");
            throw new UnauthorizedException("這個 Google 帳號的 Email 尚未驗證，無法登入");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Member member = memberDao.findByGoogleSub(google.subject()).orElse(null);
        if (member != null) {
            log.info("[會員] Google 登入 id={}", member.getId());
        } else {
            member = memberDao.findByEmail(google.email()).orElse(null);
            if (member != null) {
                if (member.getGoogleSub() != null) {
                    // 同一個 Email 已經綁了另一個 Google 帳號（很少見，例如 Google 帳號刪掉重建）
                    log.warn("[會員] Google 登入失敗：會員 id={} 已綁定其他 Google 帳號", member.getId());
                    throw new ConflictException("這個 Email 已經綁定另一個 Google 帳號");
                }
                // Google 已確認 Email 屬於本人，所以可以綁定同 Email 的既有帳號。
                // 注意：目前註冊沒有驗證 Email，有人可能先用別人的 Email 註冊。之後要加「註冊寄驗證信」補上這個洞。
                member.linkGoogle(google.subject(), google.pictureUrl());
                log.info("[會員] 既有會員 id={} 綁定 Google 帳號", member.getId());
            } else {
                member = Member.withGoogle(google.email(), google.subject(), displayNameOf(google),
                        google.pictureUrl(), now);
                log.info("[會員] 第一次用 Google 登入，建立新會員 {}", LogMasks.email(google.email()));
            }
        }

        member.recordLogin(now);
        try {
            memberDao.save(member);
        } catch (DataIntegrityViolationException e) {
            // 同一個 Google 帳號同時送兩次登入，第二個會撞到唯一索引
            log.info("[會員] Google 登入失敗：同時有另一個登入請求");
            throw new ConflictException("登入處理中，請再試一次");
        }
        log.info("[會員] Google 登入成功 id={}", member.getId());
        return issueTokens(member);
    }

    @Override
    public AuthProvidersResponse providers() {
        return new AuthProvidersResponse(googleVerifier.enabled() ? googleVerifier.clientId() : null);
    }

    /** Google 沒給名稱就用 Email @ 前面那段，太長就截斷 */
    private static String displayNameOf(GoogleUser google) {
        String name = google.name() == null || google.name().isBlank()
                ? google.email().substring(0, google.email().indexOf('@'))
                : google.name().trim();
        return name.length() > DISPLAY_NAME_MAX ? name.substring(0, DISPLAY_NAME_MAX) : name;
    }

    @Override
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("尚未登入");
        }
        RefreshTokenStore.Rotated rotated = refreshTokenStore.rotate(refreshToken);
        Member member = memberDao.findById(rotated.memberId()).orElse(null);
        if (member == null) {
            refreshTokenStore.revokeAll(rotated.memberId());
            throw new UnauthorizedException("帳號不存在，請重新登入");
        }
        AccessToken accessToken = tokenService.issueAccessToken(member);
        log.info("[會員] 換發 token 成功 id={}", member.getId());
        return new AuthResult(accessToken, rotated.refreshToken(), MemberResponse.from(member));
    }

    @Override
    public void logout(String refreshToken, String accessToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.revoke(refreshToken);
        }
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(accessToken);
                blacklist.revoke(jwt.getId(), jwt.getExpiresAt());
            } catch (JwtException e) {
                // 已經過期或無效的 token 本來就不能用，不需要放黑名單
                log.debug("[會員] 登出時的 access token 已無效，略過黑名單：{}", e.getMessage());
            }
        }
        log.info("[會員] 登出完成");
    }

    @Override
    public MemberResponse me(long memberId) {
        return memberDao.findById(memberId)
                .map(MemberResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("查無會員"));
    }

    private AuthResult issueTokens(Member member) {
        AccessToken accessToken = tokenService.issueAccessToken(member);
        String refreshToken = refreshTokenStore.issue(member.getId());
        return new AuthResult(accessToken, refreshToken, MemberResponse.from(member));
    }
}
