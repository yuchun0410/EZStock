package com.ezstock.dao.jpa;

import com.ezstock.dao.MemberDao;
import com.ezstock.model.Member;
import com.ezstock.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class MemberJpaDao implements MemberDao {

    private static final Logger log = LoggerFactory.getLogger(MemberJpaDao.class);

    private final MemberRepository memberRepository;

    public MemberJpaDao(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
        log.info("MemberDao 使用 JPA 實作");
    }

    @Override
    public Optional<Member> findById(Long id) {
        log.debug("[JPA] 查詢會員 id={}", id);
        return memberRepository.findById(id);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        // Email 屬於個資，log 只記有沒有找到，不印 Email 本身
        Optional<Member> result = memberRepository.findByEmail(Member.normalizeEmail(email));
        log.debug("[JPA] 用 Email 查詢會員：{}", result.map(m -> "找到 id=" + m.getId()).orElse("查無"));
        return result;
    }

    @Override
    public Optional<Member> findByGoogleSub(String googleSub) {
        Optional<Member> result = memberRepository.findByGoogleSub(googleSub);
        log.debug("[JPA] 用 Google 帳號查詢會員：{}", result.map(m -> "找到 id=" + m.getId()).orElse("查無"));
        return result;
    }

    @Override
    @Transactional
    public Member save(Member member) {
        boolean isNew = member.getId() == null;
        Member saved = memberRepository.save(member);
        log.info("[JPA] {}會員 id={}", isNew ? "新增" : "更新", saved.getId());
        return saved;
    }
}
