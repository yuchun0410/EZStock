package com.ezstock.dao.mybatis;

import com.ezstock.dao.StockSplitDao;
import com.ezstock.dao.mybatis.mapper.StockSplitMapper;
import com.ezstock.model.StockSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class StockSplitMyBatisDao implements StockSplitDao {

    private static final Logger log = LoggerFactory.getLogger(StockSplitMyBatisDao.class);

    private final StockSplitMapper stockSplitMapper;

    public StockSplitMyBatisDao(StockSplitMapper stockSplitMapper) {
        this.stockSplitMapper = stockSplitMapper;
        log.info("StockSplitDao 使用 MyBatis 實作");
    }

    @Override
    public List<StockSplit> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to) {
        log.debug("[MyBatis] 查詢股票分割 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<StockSplit> result = stockSplitMapper.findByStockAndDateRange(stockId, from, to);
        log.debug("[MyBatis] 查詢股票分割 stockId={} 共 {} 筆", stockId, result.size());
        return result;
    }

    @Override
    @Transactional
    public int insertAll(List<StockSplit> splits) {
        if (splits.isEmpty()) {
            log.debug("[MyBatis] 批次新增股票分割：沒有資料，略過");
            return 0;
        }
        int inserted = stockSplitMapper.insertBatch(splits);
        log.info("[MyBatis] 批次新增股票分割 完成，共 {} 筆", inserted);
        return inserted;
    }
}
