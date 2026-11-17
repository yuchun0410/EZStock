package com.ezstock.controller;

import com.ezstock.dto.MemberResponse;
import com.ezstock.service.AuthService;
import com.ezstock.service.EmailVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登入後才能用的會員 API。能進到這裡，代表 Spring Security 已經驗證過 JWT 的簽名、效期和黑名單。
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    public MemberController(AuthService authService, EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
    }

    /** 自己的會員資料；會員 id 從 JWT 的 sub 取得，不從參數拿，所以不可能查到別人的 */
    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(Long.parseLong(jwt.getSubject()));
    }

    /** 重寄驗證信；60 秒內只能寄一次 */
    @PostMapping("/me/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@AuthenticationPrincipal Jwt jwt) {
        emailVerificationService.resend(Long.parseLong(jwt.getSubject()));
    }
}
