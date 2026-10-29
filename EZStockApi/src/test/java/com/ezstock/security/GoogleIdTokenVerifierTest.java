package com.ezstock.security;

import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用自己產生的金鑰假裝是 Google，簽出各種 ID Token，確認驗證規則。
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "my-client.apps.googleusercontent.com";

    private final RsaKeys googleKeys = RsaKeys.generate();
    private final JwtEncoder googleSigner = new JwtConfig().jwtEncoder(googleKeys);
    private final GoogleIdTokenVerifier verifier = verifierTrusting(googleKeys);

    @Test
    @DisplayName("正確的 ID Token：取出帳號編號、Email、名稱、頭像")
    void valid() {
        GoogleUser user = verifier.verify(idToken(googleSigner, "https://accounts.google.com", CLIENT_ID,
                Instant.now().plusSeconds(600)));

        assertThat(user.subject()).isEqualTo("google-sub-1");
        assertThat(user.email()).isEqualTo("user@gmail.com");
        assertThat(user.emailVerified()).isTrue();
        assertThat(user.name()).isEqualTo("小明");
        assertThat(user.pictureUrl()).isEqualTo("https://pic");
        assertThat(user.toString()).doesNotContain("user@gmail.com");
    }

    @Test
    @DisplayName("發給別的網站（aud 不同）、不是 Google 發的、過期、不是 Google 簽的：全部拒絕")
    void rejected() {
        Instant later = Instant.now().plusSeconds(600);
        String otherAudience = idToken(googleSigner, "https://accounts.google.com", "other-site", later);
        String otherIssuer = idToken(googleSigner, "https://evil.example.com", CLIENT_ID, later);
        String expired = idToken(googleSigner, "https://accounts.google.com", CLIENT_ID, Instant.now().minusSeconds(600));
        String forged = idToken(new JwtConfig().jwtEncoder(RsaKeys.generate()), "https://accounts.google.com", CLIENT_ID, later);

        for (String token : List.of(otherAudience, otherIssuer, expired, forged)) {
            assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    @DisplayName("沒有設定用戶端 ID：不開放 Google 登入")
    void disabled() {
        GoogleIdTokenVerifier off = new GoogleIdTokenVerifier(new GoogleProperties(""), null);

        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(() -> off.verify("anything")).isInstanceOf(BadRequestException.class);
    }

    private static GoogleIdTokenVerifier verifierTrusting(RsaKeys keys) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(GoogleIdTokenVerifier.validator(CLIENT_ID));
        return new GoogleIdTokenVerifier(new GoogleProperties(CLIENT_ID), decoder);
    }

    private static String idToken(JwtEncoder signer, String issuer, String audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("google-sub-1")
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .claim("name", "小明")
                .claim("picture", "https://pic")
                .build();
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }
}
