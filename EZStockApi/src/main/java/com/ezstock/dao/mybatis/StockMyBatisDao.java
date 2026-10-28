package com.ezstock.dao.mybatis;

import com.ezstock.dao.StockDao;
import com.ezstock.dao.mybatis.mapper.StockMapper;
import com.ezstock.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class StockMyBatisDao implements StockDao {

    private static final Logger log = LoggerFactory.getLogger(StockMyBatisDao.class);

    private final StockMapper stockMapper;

    public StockMyBatisDao(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
        log.info("StockDao 使用 MyBatis 實作");
    }

    @Override
    public Optional<Stock> findById(String stockId) {
        log.debug("[MyBatis] 查詢股票 stockId={}", stockId);
        Optional<Stock> result = Optional.ofNullable(stockMapper.findById(stockId));
        log.debug("[MyBatis] 查詢股票 stockId={} 結果：{}", stockId, result.isPresent() ? "找到" : "查無資料");
        return result;
    }

    @Override
    public List<Stock> search(String keyword) {
        log.debug("[MyBatis] 搜尋股票 keyword={}", keyword);
        List<Stock> result = stockMapper.search(keyword);
        log.debug("[MyBatis] 搜尋股票 keyword={} 共 {} 筆", keyword, result.size());
        return result;
    }

    @Override
    public List<Stock> findAll() {
        log.debug("[MyBatis] 查詢全部股票");
        List<Stock> result = stockMapper.findAll();
        log.debug("[MyBatis] 查詢全部股票 共 {} 筆", result.size());
        return result;
    }

    @Override
    @Transactional
    public void save(Stock stock) {
        log.debug("[MyBatis] 儲存股票 stockId={} name={}", stock.getStockId(), stock.getName());
        stockMapper.upsert(stock);
        log.info("[MyBatis] 已儲存股票 {} {}", stock.getStockId(), stock.getName());
    }
}
