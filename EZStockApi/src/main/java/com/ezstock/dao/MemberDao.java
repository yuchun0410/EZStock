package com.ezstock.dao;

import com.ezstock.model.Member;

import java.util.Optional;

/**
 * 會員的存取介面。
 */
public interface MemberDao {

    Optional<Member> findById(Long id);

    /** 用 Email 查詢（會先轉小寫，不分大小寫） */
    Optional<Member> findByEmail(String email);

    /** 用 Google 帳號編號查詢 */
    Optional<Member> findByGoogleSub(String googleSub);

    /** 沒有 id 就新增（新增後 id 會填回物件），有 id 就更新 */
    Member save(Member member);
}
