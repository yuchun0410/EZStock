package com.ezstock.security;

import com.ezstock.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 簽發 access token（RS256 JWT）。
 * 內容只放驗證需要的資料：發行者、會員 id（sub）、名稱、簽發與到期時間、編號（jti）。
 * JWT 只有簽名、沒有加密，任何人都能把內容解開來看，所以不放 Email、密碼這類個資。
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(Member member) {
        // JWT 的時間只到「秒」，先去掉毫秒，回傳的 expiresAt 才會跟 token 裡的 exp 完全一樣
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String id = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(member.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(id)
                .claim("name", member.getDisplayName())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.debug("[JWT] 簽發 access token 會員 id={} jti={} 到期 {}", member.getId(), id, expiresAt);
        return new AccessToken(value, id, expiresAt);
    }
}
