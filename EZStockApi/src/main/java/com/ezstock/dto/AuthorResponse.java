package com.ezstock.dto;

import com.ezstock.model.Member;

/** 文章、留言的作者：只公開名稱和頭像，不公開 Email */
public record AuthorResponse(Long id, String displayName, String avatarUrl) {

    public static AuthorResponse from(Member member) {
        return new AuthorResponse(member.getId(), member.getDisplayName(), member.getAvatarUrl());
    }
}
