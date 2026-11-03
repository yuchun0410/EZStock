package com.ezstock.dao.mybatis;

import com.ezstock.dao.PriceAlertDao;
import com.ezstock.dao.mybatis.mapper.PriceAlertMapper;
import com.ezstock.model.PriceAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class PriceAlertMyBatisDao implements PriceAlertDao {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertMyBatisDao.class);

    private final PriceAlertMapper mapper;

    public PriceAlertMyBatisDao(PriceAlertMapper mapper) {
        this.mapper = mapper;
        log.info("PriceAlertDao 使用 MyBatis 實作");
    }

    @Override
    public List<PriceAlert> findActiveByMember(long memberId) {
        List<PriceAlert> alerts = mapper.findActiveByMember(memberId);
        log.debug("[MyBatis] 查詢到價提醒 會員 id={} 共 {} 個", memberId, alerts.size());
        return alerts;
    }

    @Override
    @Transactional
    public PriceAlert save(PriceAlert alert) {
        if (alert.getId() == null) {
            mapper.insert(alert);
            log.debug("[MyBatis] 新增到價提醒 id={}", alert.getId());
        } else {
            mapper.updateTriggeredAt(alert);
            log.debug("[MyBatis] 更新到價提醒 id={}", alert.getId());
        }
        return alert;
    }

    @Override
    @Transactional
    public boolean deleteByIdAndMember(long id, long memberId) {
        return mapper.deleteByIdAndMember(id, memberId) > 0;
    }
}
