package com.ezstock.dao.jpa;

import com.ezstock.dao.DividendDao;
import com.ezstock.model.Dividend;
import com.ezstock.repository.DividendRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class DividendJpaDao implements DividendDao {

    private static final Logger log = LoggerFactory.getLogger(DividendJpaDao.class);

    private final DividendRepository dividendRepository;

    public DividendJpaDao(DividendRepository dividendRepository) {
        this.dividendRepository = dividendRepository;
        log.info("DividendDao 使用 JPA 實作");
    }

    @Override
    public List<Dividend> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to) {
        log.debug("[JPA] 查詢除息 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<Dividend> result = dividendRepository
                .findByStock_StockIdAndExDateBetweenOrderByExDateAsc(stockId, from, to);
        log.debug("[JPA] 查詢除息 stockId={} 共 {} 筆", stockId, result.size());
        return result;
    }

    @Override
    @Transactional
    public int insertAll(List<Dividend> dividends) {
        if (dividends.isEmpty()) {
            log.debug("[JPA] 批次新增除息：沒有資料，略過");
            return 0;
        }
        log.debug("[JPA] 批次新增除息 開始，共 {} 筆", dividends.size());
        dividendRepository.saveAll(dividends);
        log.info("[JPA] 批次新增除息 完成，共 {} 筆", dividends.size());
        return dividends.size();
    }
}
