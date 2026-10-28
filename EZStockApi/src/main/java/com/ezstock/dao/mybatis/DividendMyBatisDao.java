package com.ezstock.dao.mybatis;

import com.ezstock.dao.DividendDao;
import com.ezstock.dao.mybatis.mapper.DividendMapper;
import com.ezstock.model.Dividend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class DividendMyBatisDao implements DividendDao {

    private static final Logger log = LoggerFactory.getLogger(DividendMyBatisDao.class);

    private final DividendMapper dividendMapper;

    public DividendMyBatisDao(DividendMapper dividendMapper) {
        this.dividendMapper = dividendMapper;
        log.info("DividendDao 使用 MyBatis 實作");
    }

    @Override
    public List<Dividend> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to) {
        log.debug("[MyBatis] 查詢除息 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<Dividend> result = dividendMapper.findByStockAndDateRange(stockId, from, to);
        log.debug("[MyBatis] 查詢除息 stockId={} 共 {} 筆", stockId, result.size());
        return result;
    }

    @Override
    @Transactional
    public int insertAll(List<Dividend> dividends) {
        if (dividends.isEmpty()) {
            log.debug("[MyBatis] 批次新增除息：沒有資料，略過");
            return 0;
        }
        log.debug("[MyBatis] 批次新增除息 開始，共 {} 筆", dividends.size());
        int inserted = dividendMapper.insertBatch(dividends);
        log.info("[MyBatis] 批次新增除息 完成，共 {} 筆", inserted);
        return inserted;
    }
}
