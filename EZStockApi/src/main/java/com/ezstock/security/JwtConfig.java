package com.ezstock.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 的簽發（JwtEncoder，用私鑰）與驗證（JwtDecoder，用公鑰），演算法固定 RS256。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RsaKeys rsaKeys(JwtProperties properties) {
        if (properties.privateKey() == null || properties.privateKey().isBlank()) {
            RsaKeys keys = RsaKeys.generate();
            log.warn("[JWT] 沒有設定 EZSTOCK_JWT_PRIVATE_KEY，使用臨時金鑰 kid={}；重開後先前發的 token 會全部失效",
                    keys.keyId());
            return keys;
        }
        RsaKeys keys = RsaKeys.fromPrivateKey(properties.privateKey());
        log.info("[JWT] 已載入 RS256 金鑰 kid={}（{} bits），access token 效期 {}",
                keys.keyId(), keys.modulus().bitLength(), properties.accessTokenTtl());
        return keys;
    }

    @Bean
    public JwtEncoder jwtEncoder(RsaKeys keys) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(keys.keyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** 只接受 RS256，並檢查效期與發行者（iss）；不接受 none 或 HS256 這類被竄改的 header */
    @Bean
    public JwtDecoder jwtDecoder(RsaKeys keys, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
