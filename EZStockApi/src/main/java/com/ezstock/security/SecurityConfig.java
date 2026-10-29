package com.ezstock.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 設定。
 * - 無狀態（STATELESS）：不用 session，每個請求自己帶 Authorization: Bearer <JWT>
 * - 查股價、回測、選股維持公開；之後的自選股、會員資料才需要登入
 * - 沒帶 token 或 token 無效，回 JSON 格式的 401，跟其他錯誤的格式一致
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 是利用瀏覽器「自動帶 cookie」的攻擊；API 靠 Authorization header 驗證，瀏覽器不會自動帶，所以關掉
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/members/me", "/api/members/me/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, e) -> {
                            // 不印 token，只記路徑和原因
                            log.warn("未登入或 token 無效：{} {}（{}）", request.getMethod(), request.getRequestURI(),
                                    e.getMessage());
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("{\"status\":401,\"message\":\"請先登入\"}");
                        }));
        return http.build();
    }
}
