package com.ezstock.dto;

import com.ezstock.model.Member;

import java.time.LocalDateTime;

/**
 * 回給前端的會員資料（沒有密碼雜湊、沒有 Google 帳號編號）。
 *
 * @param hasPassword  有沒有設定密碼（只用 Google 登入的會員沒有）
 * @param googleLinked 有沒有綁定 Google
 */
public record MemberResponse(Long id, String email, String displayName, String avatarUrl,
                             boolean hasPassword, boolean googleLinked, LocalDateTime createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getDisplayName(), member.getAvatarUrl(),
                member.hasPassword(), member.getGoogleSub() != null, member.getCreatedAt());
    }
}
