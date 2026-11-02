package com.ezstock.dto;

/**
 * 登入成功的回應。refresh token 不在這裡，它放在 HttpOnly cookie，JavaScript 讀不到。
 *
 * @param accessToken JWT，之後每個請求放在 Authorization: Bearer 後面
 * @param tokenType   固定是 Bearer
 * @param expiresIn   幾秒後過期，前端可以提早換新的
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, MemberResponse member) {

    @Override
    public String toString() {
        return "TokenResponse[expiresIn=" + expiresIn + ", member=" + member.id() + "]";
    }
}
