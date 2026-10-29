package com.ezstock.dao.mybatis;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.mybatis.mapper.DailyPriceMapper;
import com.ezstock.model.DailyPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class DailyPriceMyBatisDao implements DailyPriceDao {

    private static final Logger log = LoggerFactory.getLogger(DailyPriceMyBatisDao.class);

    /** 每批最多幾筆，避免一條 SQL 太長 */
    private static final int BATCH_SIZE = 500;

    private final DailyPriceMapper dailyPriceMapper;

    public DailyPriceMyBatisDao(DailyPriceMapper dailyPriceMapper) {
        this.dailyPriceMapper = dailyPriceMapper;
        log.info("DailyPriceDao 使用 MyBatis 實作");
    }

    @Override
    public List<DailyPrice> findByStockAndDateRange(String stockId, LocalDate from, LocalDate to) {
        log.debug("[MyBatis] 查詢日線 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<DailyPrice> result = dailyPriceMapper.findByStockAndDateRange(stockId, from, to);
        log.debug("[MyBatis] 查詢日線 stockId={} 共 {} 筆", stockId, result.size());
        return result;
    }

    @Override
    public Optional<DailyPrice> findLatest(String stockId) {
        log.debug("[MyBatis] 查詢最新日線 stockId={}", stockId);
        Optional<DailyPrice> result = Optional.ofNullable(dailyPriceMapper.findLatest(stockId));
        log.debug("[MyBatis] 查詢最新日線 stockId={} 結果：{}", stockId,
                result.map(p -> p.getTradeDate().toString()).orElse("查無資料"));
        return result;
    }

    @Override
    public List<String> findStockIdsWithPrices() {
        log.debug("[MyBatis] 查詢有價格資料的股票");
        List<String> result = dailyPriceMapper.findStockIdsWithPrices();
        log.debug("[MyBatis] 查詢有價格資料的股票 共 {} 檔", result.size());
        return result;
    }

    @Override
    public List<LocalDate> findTradeDates(String stockId, LocalDate from, LocalDate to) {
        log.debug("[MyBatis] 查詢已存在交易日 stockId={} 期間 {} ~ {}", stockId, from, to);
        List<LocalDate> result = dailyPriceMapper.findTradeDates(stockId, from, to);
        log.debug("[MyBatis] 已存在交易日 stockId={} 共 {} 天", stockId, result.size());
        return result;
    }

    @Override
    @Transactional
    public int insertAll(List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            log.debug("[MyBatis] 批次新增日線：沒有資料，略過");
            return 0;
        }
        log.debug("[MyBatis] 批次新增日線 開始，共 {} 筆，每批 {} 筆", prices.size(), BATCH_SIZE);
        int inserted = 0;
        for (int start = 0; start < prices.size(); start += BATCH_SIZE) {
            List<DailyPrice> chunk = prices.subList(start, Math.min(start + BATCH_SIZE, prices.size()));
            inserted += dailyPriceMapper.insertBatch(chunk);
            log.debug("[MyBatis] 批次新增日線 進度 {}/{}", Math.min(start + BATCH_SIZE, prices.size()), prices.size());
        }
        log.info("[MyBatis] 批次新增日線 完成，共 {} 筆", inserted);
        return inserted;
    }
}
