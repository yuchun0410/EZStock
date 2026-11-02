package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.MoneyAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MoneyAccountMapper {

    List<MoneyAccount> findByMember(@Param("memberId") long memberId);

    MoneyAccount findByIdAndMember(@Param("id") long id, @Param("memberId") long memberId);

    /** 新增後，資料庫產生的 id 會填回 account */
    int insert(MoneyAccount account);

    /** WHERE 同時比對 id 與會員 id，不是自己的帳戶改不到 */
    int update(MoneyAccount account);

    int deleteByIdAndMember(@Param("id") long id, @Param("memberId") long memberId);
}
