package com.ezstock.service;

import com.ezstock.dto.LoginRequest;
import com.ezstock.dto.MemberResponse;
import com.ezstock.dto.RegisterRequest;
import com.ezstock.security.AccessToken;

public interface AuthService {

    /** 註冊並直接登入 */
    AuthResult register(RegisterRequest request);

    AuthResult login(LoginRequest request);

    /** 用 refresh token 換新的 access token，refresh token 也會換新的（輪替） */
    AuthResult refresh(String refreshToken);

    /** 作廢這個裝置的 refresh token；有帶 access token 的話也加入黑名單。兩個都可以是 null */
    void logout(String refreshToken, String accessToken);

    MemberResponse me(long memberId);

    /**
     * 登入成功的結果。refreshToken 由 Controller 放進 cookie，不能出現在回應 body 或 log。
     */
    record AuthResult(AccessToken accessToken, String refreshToken, MemberResponse member) {

        @Override
        public String toString() {
            return "AuthResult[member=" + member.id() + ", accessToken=" + accessToken + "]";
        }
    }
}
