package com.ezstock.dao;

import com.ezstock.model.MoneyAccount;

import java.util.List;
import java.util.Optional;

/**
 * 現金、負債帳戶的存取介面。查單筆一定要帶會員 id，避免用別人的帳戶 id 查到別人的資料。
 */
public interface MoneyAccountDao {

    /** 某個會員的所有帳戶，依種類、id 排序 */
    List<MoneyAccount> findByMember(long memberId);

    Optional<MoneyAccount> findByIdAndMember(long id, long memberId);

    /** 沒有 id 就新增（新增後 id 會填回物件），有 id 就更新 */
    MoneyAccount save(MoneyAccount account);

    /** 回傳是否真的有刪到（不是自己的帳戶就刪不到） */
    boolean deleteByIdAndMember(long id, long memberId);
}
