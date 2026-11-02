package com.ezstock.security;

import com.ezstock.config.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 設定。
 * - 無狀態（STATELESS）：不用 session，每個請求自己帶 Authorization: Bearer <JWT>
 * - 查股價、回測、選股維持公開；會員資料、之後的自選股才需要登入
 * - 沒帶 token 或 token 無效，回跟其他錯誤一樣格式的 JSON 401
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 是利用瀏覽器「自動帶 cookie」的攻擊。一般 API 靠 Authorization header，瀏覽器不會自動帶；
                // 唯一用 cookie 的 /api/auth/refresh、/logout，cookie 設了 SameSite=Strict，別的網站發的請求不會帶上
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/members/me", "/api/members/me/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, e) -> {
                            // 不印 token，只記路徑和原因
                            log.warn("未登入或 token 無效：{} {}（{}）", request.getMethod(), request.getRequestURI(),
                                    e.getMessage());
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            String traceId = MDC.get(RequestLoggingFilter.TRACE_ID);
                            response.getWriter().write("{\"status\":401,\"error\":\"UNAUTHORIZED\","
                                    + "\"message\":\"請先登入\",\"traceId\":"
                                    + (traceId == null ? "null" : "\"" + traceId + "\"") + "}");
                        }));
        return http.build();
    }

    /**
     * /api/auth/** 不經過 JWT 驗證：登出時帶著過期的 access token 也要能登出，
     * 由 AuthService 自己解析（解析失敗就忽略），不能在這裡被擋成 401。
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) ->
                request.getRequestURI().startsWith("/api/auth/") ? null : resolver.resolve(request);
    }

    /** BCrypt：每次雜湊都加隨機鹽，而且刻意算得慢（預設 2^10 輪），讓暴力破解很花時間 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
