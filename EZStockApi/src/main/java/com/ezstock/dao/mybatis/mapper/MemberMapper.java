package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.Member;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper {

    Member findById(Long id);

    Member findByEmail(String email);

    Member findByGoogleSub(String googleSub);

    /** 新增後，資料庫產生的 id 會填回 member */
    int insert(Member member);

    int update(Member member);
}
