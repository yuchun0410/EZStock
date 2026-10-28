package com.ezstock.dao.jpa;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.model.DailyPrice;
import com.ezstock.repository.DailyPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class DailyPriceJpaDao implements DailyPriceDao {

    private static final Logger log = LoggerFactory.getLogger(DailyPriceJpaDao.class);

    private final DailyPriceRepository dailyPriceRepository;

    public DailyPriceJpaDao(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
        log.info("DailyPriceDao 使用 JPA 實作");
    }

    @Override
    public List<DailyPrice> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to) {
        log.debug("[JPA] 查詢日線 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<DailyPrice> result = dailyPriceRepository
                .findByStock_StockIdAndTradeDateBetweenOrderByTradeDateAsc(stockId, from, to);
        log.debug("[JPA] 查詢日線 stockId={} 共 {} 筆", stockId, result.size());
        return result;
    }

    @Override
    public Optional<DailyPrice> findLatest(String stockId) {
        log.debug("[JPA] 查詢最新日線 stockId={}", stockId);
        Optional<DailyPrice> result = dailyPriceRepository.findFirstByStock_StockIdOrderByTradeDateDesc(stockId);
        log.debug("[JPA] 查詢最新日線 stockId={} 結果：{}", stockId,
                result.map(p -> p.getTradeDate().toString()).orElse("查無資料"));
        return result;
    }

    @Override
    public List<LocalDate> findTradeDates(String stockId, LocalDate from, LocalDate to) {
        log.debug("[JPA] 查詢已存在交易日 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<LocalDate> result = dailyPriceRepository.findTradeDates(stockId, from, to);
        log.debug("[JPA] 已存在交易日 stockId={} 共 {} 天", stockId, result.size());
        return result;
    }

    @Override
    @Transactional
    public int insertAll(List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            log.debug("[JPA] 批次新增日線：沒有資料，略過");
            return 0;
        }
        log.debug("[JPA] 批次新增日線 開始，共 {} 筆", prices.size());
        dailyPriceRepository.saveAll(prices);
        log.info("[JPA] 批次新增日線 完成，共 {} 筆", prices.size());
        return prices.size();
    }
}
