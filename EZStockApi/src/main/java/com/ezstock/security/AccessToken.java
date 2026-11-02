package com.ezstock.security;

import java.time.Instant;

/**
 * 簽發出去的 access token。
 *
 * @param value     JWT 字串（只回給前端，不能寫進 log）
 * @param id        JWT 編號（jti），登出時用它加入黑名單
 * @param expiresAt 到期時間
 */
public record AccessToken(String value, String id, Instant expiresAt) {

    @Override
    public String toString() {
        return "AccessToken[id=" + id + ", expiresAt=" + expiresAt + "]";
    }
}
