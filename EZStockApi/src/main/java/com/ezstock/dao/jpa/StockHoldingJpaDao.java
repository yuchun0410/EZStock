package com.ezstock.dao.jpa;

import com.ezstock.dao.StockHoldingDao;
import com.ezstock.model.StockHolding;
import com.ezstock.repository.StockHoldingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class StockHoldingJpaDao implements StockHoldingDao {

    private static final Logger log = LoggerFactory.getLogger(StockHoldingJpaDao.class);

    private final StockHoldingRepository repository;

    public StockHoldingJpaDao(StockHoldingRepository repository) {
        this.repository = repository;
        log.info("StockHoldingDao 使用 JPA 實作");
    }

    @Override
    public List<StockHolding> findByMember(long memberId) {
        List<StockHolding> holdings = repository.findByMember(memberId);
        log.debug("[JPA] 查詢持股 會員 id={} 共 {} 檔", memberId, holdings.size());
        return holdings;
    }

    /** 主鍵已經給定，save 會走 merge：有就更新、沒有就新增 */
    @Override
    @Transactional
    public void upsert(StockHolding holding) {
        repository.save(holding);
        log.debug("[JPA] 儲存持股 會員 id={} {}", holding.getMemberId(), holding.getId().getStockId());
    }

    @Override
    @Transactional
    public boolean delete(long memberId, String stockId) {
        StockHolding.Key key = new StockHolding.Key(memberId, stockId);
        if (!repository.existsById(key)) {
            return false;
        }
        repository.deleteById(key);
        log.debug("[JPA] 刪除持股 會員 id={} {}", memberId, stockId);
        return true;
    }
}
