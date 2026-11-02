package com.ezstock.dao.mybatis;

import com.ezstock.dao.StockHoldingDao;
import com.ezstock.dao.mybatis.mapper.StockHoldingMapper;
import com.ezstock.model.StockHolding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class StockHoldingMyBatisDao implements StockHoldingDao {

    private static final Logger log = LoggerFactory.getLogger(StockHoldingMyBatisDao.class);

    private final StockHoldingMapper mapper;

    public StockHoldingMyBatisDao(StockHoldingMapper mapper) {
        this.mapper = mapper;
        log.info("StockHoldingDao 使用 MyBatis 實作");
    }

    @Override
    public List<StockHolding> findByMember(long memberId) {
        List<StockHolding> holdings = mapper.findByMember(memberId);
        log.debug("[MyBatis] 查詢持股 會員 id={} 共 {} 檔", memberId, holdings.size());
        return holdings;
    }

    @Override
    @Transactional
    public void upsert(StockHolding holding) {
        mapper.upsert(holding);
        log.debug("[MyBatis] 儲存持股 會員 id={} {}", holding.getMemberId(), holding.getId().getStockId());
    }

    @Override
    @Transactional
    public boolean delete(long memberId, String stockId) {
        boolean deleted = mapper.delete(memberId, stockId) > 0;
        log.debug("[MyBatis] 刪除持股 會員 id={} {}：{}", memberId, stockId, deleted ? "成功" : "查無");
        return deleted;
    }
}
