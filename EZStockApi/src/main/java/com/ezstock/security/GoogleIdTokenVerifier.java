package com.ezstock.security;

import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 驗證前端從 Google 拿到的 ID Token（一個由 Google 用 RS256 簽名的 JWT）。
 *
 * 跟驗證我們自己的 JWT 是同一套機制，只是公鑰換成 Google 公開的那幾把（JWKS，會自動快取與更新），並檢查：
 * - 簽名：真的是 Google 簽的
 * - iss：發行者是 Google
 * - aud：是發給「我們的」用戶端 ID 的，別的網站拿到的 token 不能拿來登入我們的網站
 * - exp：還沒過期
 *
 * 前端傳來的 Email、名稱一律不信任，只用驗證過的 token 內容。
 */
@Component
public class GoogleIdTokenVerifier {

    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private final GoogleProperties properties;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleIdTokenVerifier(GoogleProperties properties) {
        this(properties, properties.enabled() ? googleDecoder(properties.clientId()) : null);
        if (properties.enabled()) {
            log.info("[Google] 已開放 Google 登入");
        } else {
            log.info("[Google] 沒有設定 ezstock.google.client-id，不開放 Google 登入");
        }
    }

    /** 測試用：換成用測試金鑰的 decoder */
    GoogleIdTokenVerifier(GoogleProperties properties, JwtDecoder decoder) {
        this.properties = properties;
        this.decoder = decoder;
    }

    private static JwtDecoder googleDecoder(String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator(clientId));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String clientId) {
        // Google 的 iss 可能是「accounts.google.com」（沒有 https），不能用 jwt.getIssuer()：它會轉成 URL 而失敗
        OAuth2TokenValidator<Jwt> issuer = jwt -> jwt.getClaimAsString("iss") != null && ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "不是 Google 發的 token", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "token 不是發給本網站的", null));
        return new DelegatingOAuth2TokenValidator<>(List.of(new JwtTimestampValidator(), issuer, audience));
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public String clientId() {
        return properties.clientId();
    }

    public GoogleUser verify(String idToken) {
        if (decoder == null) {
            throw new BadRequestException("目前沒有開放 Google 登入");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // 不印 token 本身
            log.warn("[Google] ID Token 驗證失敗：{}", e.getMessage());
            throw new UnauthorizedException("Google 登入失敗，請再試一次");
        }
        GoogleUser user = new GoogleUser(jwt.getSubject(), jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
        log.debug("[Google] ID Token 驗證成功 {}", user);
        return user;
    }
}
