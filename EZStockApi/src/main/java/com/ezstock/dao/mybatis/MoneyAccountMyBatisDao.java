package com.ezstock.dao.mybatis;

import com.ezstock.dao.MoneyAccountDao;
import com.ezstock.dao.mybatis.mapper.MoneyAccountMapper;
import com.ezstock.model.MoneyAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class MoneyAccountMyBatisDao implements MoneyAccountDao {

    private static final Logger log = LoggerFactory.getLogger(MoneyAccountMyBatisDao.class);

    private final MoneyAccountMapper mapper;

    public MoneyAccountMyBatisDao(MoneyAccountMapper mapper) {
        this.mapper = mapper;
        log.info("MoneyAccountDao 使用 MyBatis 實作");
    }

    @Override
    public List<MoneyAccount> findByMember(long memberId) {
        List<MoneyAccount> accounts = mapper.findByMember(memberId);
        log.debug("[MyBatis] 查詢帳戶 會員 id={} 共 {} 筆", memberId, accounts.size());
        return accounts;
    }

    @Override
    public Optional<MoneyAccount> findByIdAndMember(long id, long memberId) {
        return Optional.ofNullable(mapper.findByIdAndMember(id, memberId));
    }

    @Override
    @Transactional
    public MoneyAccount save(MoneyAccount account) {
        if (account.getId() == null) {
            mapper.insert(account);
            log.debug("[MyBatis] 新增帳戶 id={} 會員 id={}", account.getId(), account.getMemberId());
        } else {
            mapper.update(account);
            log.debug("[MyBatis] 更新帳戶 id={} 會員 id={}", account.getId(), account.getMemberId());
        }
        return account;
    }

    @Override
    @Transactional
    public boolean deleteByIdAndMember(long id, long memberId) {
        boolean deleted = mapper.deleteByIdAndMember(id, memberId) > 0;
        log.debug("[MyBatis] 刪除帳戶 id={} 會員 id={}：{}", id, memberId, deleted ? "成功" : "查無");
        return deleted;
    }
}
