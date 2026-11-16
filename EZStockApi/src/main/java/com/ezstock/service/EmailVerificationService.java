package com.ezstock.service;

import com.ezstock.dto.MemberResponse;
import com.ezstock.model.Member;

/**
 * Email 驗證：註冊後寄驗證信，使用者點信裡的連結就算驗證完成。
 */
public interface EmailVerificationService {

    /**
     * 寄驗證信。沒有 Email、或已經驗證過就不寄。
     * 寄信失敗只記 log、不往外丟：不能因為寄信服務掛掉就讓註冊失敗，之後可以再重寄。
     */
    void sendVerification(Member member);

    /** 使用者點了驗證連結；token 無效、過期或用過回 400 */
    MemberResponse verify(String token);

    /** 重寄驗證信（要登入）；已驗證回 400，60 秒內重複要求回 429 */
    void resend(long memberId);
}
