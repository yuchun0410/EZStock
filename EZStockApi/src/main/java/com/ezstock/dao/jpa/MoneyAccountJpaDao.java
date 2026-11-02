package com.ezstock.dao.jpa;

import com.ezstock.dao.MoneyAccountDao;
import com.ezstock.model.MoneyAccount;
import com.ezstock.repository.MoneyAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class MoneyAccountJpaDao implements MoneyAccountDao {

    private static final Logger log = LoggerFactory.getLogger(MoneyAccountJpaDao.class);

    private final MoneyAccountRepository repository;

    public MoneyAccountJpaDao(MoneyAccountRepository repository) {
        this.repository = repository;
        log.info("MoneyAccountDao 使用 JPA 實作");
    }

    @Override
    public List<MoneyAccount> findByMember(long memberId) {
        List<MoneyAccount> accounts = repository.findByMemberIdOrderByKindAscIdAsc(memberId);
        log.debug("[JPA] 查詢帳戶 會員 id={} 共 {} 筆", memberId, accounts.size());
        return accounts;
    }

    @Override
    public Optional<MoneyAccount> findByIdAndMember(long id, long memberId) {
        return repository.findByIdAndMemberId(id, memberId);
    }

    @Override
    @Transactional
    public MoneyAccount save(MoneyAccount account) {
        boolean isNew = account.getId() == null;
        MoneyAccount saved = repository.save(account);
        log.debug("[JPA] {}帳戶 id={} 會員 id={}", isNew ? "新增" : "更新", saved.getId(), saved.getMemberId());
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteByIdAndMember(long id, long memberId) {
        boolean deleted = repository.deleteByIdAndMemberId(id, memberId) > 0;
        log.debug("[JPA] 刪除帳戶 id={} 會員 id={}：{}", id, memberId, deleted ? "成功" : "查無");
        return deleted;
    }
}
