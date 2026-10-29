package com.ezstock.security;

import com.ezstock.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    private final JwtProperties properties = new JwtProperties(null, "ezstock", null, null, false);
    private final JwtConfig config = new JwtConfig();
    private final RsaKeys keys = RsaKeys.generate();

    private AccessTokenBlacklist blacklist;
    private TokenService tokenService;
    private JwtDecoder decoder;
    private Member member;

    @BeforeEach
    void setUp() {
        blacklist = mock(AccessTokenBlacklist.class);
        when(blacklist.isRevoked(anyString())).thenReturn(false);
        tokenService = new TokenService(config.jwtEncoder(keys), properties, Clock.systemUTC());
        decoder = config.jwtDecoder(keys, properties, blacklist);
        member = Member.withPassword("a@example.com", "hash", "小明", LocalDateTime.now());
        ReflectionTestUtils.setField(member, "id", 42L);
    }

    @Test
    @DisplayName("簽發的 token：RS256、帶 kid，用公鑰驗證後拿得到會員 id、名稱、編號")
    void issueAndDecode() {
        AccessToken token = tokenService.issueAccessToken(member);

        Jwt jwt = decoder.decode(token.value());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", keys.keyId());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("小明");
        assertThat(jwt.getId()).isEqualTo(token.id());
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(jwt.getClaims()).doesNotContainKey("email");
    }

    @Test
    @DisplayName("別把金鑰簽的 token：驗證失敗")
    void otherKey() {
        TokenService attacker = new TokenService(config.jwtEncoder(RsaKeys.generate()), properties, Clock.systemUTC());
        String forged = attacker.issueAccessToken(member).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("內容被竄改（改成別的會員 id）：簽名對不上，驗證失敗")
    void tampered() {
        String[] parts = tokenService.issueAccessToken(member).value().split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("\"42\"", "\"1\"");
        String forged = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes()) + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("alg 改成 none、拿掉簽名：驗證失敗")
    void algNone() {
        String[] parts = tokenService.issueAccessToken(member).value().split("\\.");
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes());

        assertThatThrownBy(() -> decoder.decode(header + "." + parts[1] + ".")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("已登出（在黑名單）：驗證失敗")
    void revoked() {
        AccessToken token = tokenService.issueAccessToken(member);
        when(blacklist.isRevoked(token.id())).thenReturn(true);

        assertThatThrownBy(() -> decoder.decode(token.value()))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("已登出");
    }

    @Test
    @DisplayName("發行者不同：驗證失敗")
    void wrongIssuer() {
        JwtProperties other = new JwtProperties(null, "someone-else", null, null, false);
        String token = new TokenService(config.jwtEncoder(keys), other, Clock.systemUTC())
                .issueAccessToken(member).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
