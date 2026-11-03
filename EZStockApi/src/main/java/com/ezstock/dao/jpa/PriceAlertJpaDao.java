package com.ezstock.dao.jpa;

import com.ezstock.dao.PriceAlertDao;
import com.ezstock.model.PriceAlert;
import com.ezstock.repository.PriceAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class PriceAlertJpaDao implements PriceAlertDao {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertJpaDao.class);

    private final PriceAlertRepository repository;

    public PriceAlertJpaDao(PriceAlertRepository repository) {
        this.repository = repository;
        log.info("PriceAlertDao 使用 JPA 實作");
    }

    @Override
    public List<PriceAlert> findActiveByMember(long memberId) {
        List<PriceAlert> alerts = repository.findActiveByMember(memberId);
        log.debug("[JPA] 查詢到價提醒 會員 id={} 共 {} 個", memberId, alerts.size());
        return alerts;
    }

    @Override
    @Transactional
    public PriceAlert save(PriceAlert alert) {
        boolean isNew = alert.getId() == null;
        PriceAlert saved = repository.save(alert);
        log.debug("[JPA] {}到價提醒 id={}", isNew ? "新增" : "更新", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteByIdAndMember(long id, long memberId) {
        return repository.deleteByIdAndMemberId(id, memberId) > 0;
    }
}
