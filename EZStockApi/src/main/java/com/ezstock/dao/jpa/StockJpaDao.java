package com.ezstock.dao.jpa;

import com.ezstock.dao.StockDao;
import com.ezstock.model.Stock;
import com.ezstock.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class StockJpaDao implements StockDao {

    private static final Logger log = LoggerFactory.getLogger(StockJpaDao.class);

    private final StockRepository stockRepository;

    public StockJpaDao(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
        log.info("StockDao 使用 JPA 實作");
    }

    @Override
    public Optional<Stock> findById(String stockId) {
        log.debug("[JPA] 查詢股票 stockId={}", stockId);
        Optional<Stock> result = stockRepository.findById(stockId);
        log.debug("[JPA] 查詢股票 stockId={} 結果：{}", stockId, result.isPresent() ? "找到" : "查無資料");
        return result;
    }

    @Override
    public List<Stock> search(String keyword) {
        log.debug("[JPA] 搜尋股票 keyword={}", keyword);
        List<Stock> result = stockRepository
                .findByStockIdContainingOrNameContainingOrderByStockIdAsc(keyword, keyword);
        log.debug("[JPA] 搜尋股票 keyword={} 共 {} 筆", keyword, result.size());
        return result;
    }

    @Override
    public List<Stock> findAll() {
        log.debug("[JPA] 查詢全部股票");
        List<Stock> result = stockRepository.findAll(Sort.by("stockId"));
        log.debug("[JPA] 查詢全部股票 共 {} 筆", result.size());
        return result;
    }

    @Override
    @Transactional
    public void save(Stock stock) {
        log.debug("[JPA] 儲存股票 stockId={} name={}", stock.getStockId(), stock.getName());
        stockRepository.save(stock);
        log.info("[JPA] 已儲存股票 {} {}", stock.getStockId(), stock.getName());
    }
}
