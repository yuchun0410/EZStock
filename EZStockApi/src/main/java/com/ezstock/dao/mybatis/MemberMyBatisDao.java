package com.ezstock.dao.mybatis;

import com.ezstock.dao.MemberDao;
import com.ezstock.dao.mybatis.mapper.MemberMapper;
import com.ezstock.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class MemberMyBatisDao implements MemberDao {

    private static final Logger log = LoggerFactory.getLogger(MemberMyBatisDao.class);

    private final MemberMapper memberMapper;

    public MemberMyBatisDao(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
        log.info("MemberDao 使用 MyBatis 實作");
    }

    @Override
    public Optional<Member> findById(Long id) {
        log.debug("[MyBatis] 查詢會員 id={}", id);
        return Optional.ofNullable(memberMapper.findById(id));
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        Optional<Member> result = Optional.ofNullable(memberMapper.findByEmail(Member.normalizeEmail(email)));
        log.debug("[MyBatis] 用 Email 查詢會員：{}", result.map(m -> "找到 id=" + m.getId()).orElse("查無"));
        return result;
    }

    @Override
    public Optional<Member> findByGoogleSub(String googleSub) {
        Optional<Member> result = Optional.ofNullable(memberMapper.findByGoogleSub(googleSub));
        log.debug("[MyBatis] 用 Google 帳號查詢會員：{}", result.map(m -> "找到 id=" + m.getId()).orElse("查無"));
        return result;
    }

    @Override
    @Transactional
    public Member save(Member member) {
        if (member.getId() == null) {
            memberMapper.insert(member);
            log.info("[MyBatis] 新增會員 id={}", member.getId());
        } else {
            memberMapper.update(member);
            log.info("[MyBatis] 更新會員 id={}", member.getId());
        }
        return member;
    }
}
