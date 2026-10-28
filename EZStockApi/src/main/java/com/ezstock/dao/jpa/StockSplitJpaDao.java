package com.ezstock.dao.jpa;

import com.ezstock.dao.StockSplitDao;
import com.ezstock.model.StockSplit;
import com.ezstock.repository.StockSplitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class StockSplitJpaDao implements StockSplitDao {

    private static final Logger log = LoggerFactory.getLogger(StockSplitJpaDao.class);

    private final StockSplitRepository stockSplitRepository;

    public StockSplitJpaDao(StockSplitRepository stockSplitRepository) {
        this.stockSplitRepository = stockSplitRepository;
        log.info("StockSplitDao 使用 JPA 實作");
    }

    @Override
    public List<StockSplit> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to) {
        log.debug("[JPA] 查詢股票分割 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<StockSplit> result = stockSplitRepository
                .findByStock_StockIdAndSplitDateBetweenOrderBySplitDateAsc(stockId, from, to);
        log.debug("[JPA] 查詢股票分割 stockId={} 共 {} 筆", stockId, result.size());
        return result;
    }

    @Override
    @Transactional
    public int insertAll(List<StockSplit> splits) {
        if (splits.isEmpty()) {
            log.debug("[JPA] 批次新增股票分割：沒有資料，略過");
            return 0;
        }
        stockSplitRepository.saveAll(splits);
        log.info("[JPA] 批次新增股票分割 完成，共 {} 筆", splits.size());
        return splits.size();
    }
}
