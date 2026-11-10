package com.ezstock.dto;

import com.ezstock.model.Member;

import java.time.LocalDateTime;

/**
 * 回給前端的會員資料（沒有密碼雜湊、沒有 Google 帳號編號）。
 *
 * @param hasPassword  有沒有設定密碼（只用 Google 登入的會員沒有）
 * @param email        只用 LINE 登入、沒提供 Email 時是 null
 * @param googleLinked 有沒有綁定 Google
 * @param lineLinked   有沒有綁定 LINE（用 LINE 登入，或在網站上用綁定碼綁過 LINE Bot）
 */
public record MemberResponse(Long id, String email, String displayName, String avatarUrl,
                             boolean hasPassword, boolean googleLinked, boolean lineLinked, LocalDateTime createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getDisplayName(), member.getAvatarUrl(),
                member.hasPassword(), member.getGoogleSub() != null, member.getLineUserId() != null,
                member.getCreatedAt());
    }
}
