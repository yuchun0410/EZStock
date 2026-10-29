package com.ezstock.dao.jpa;

import com.ezstock.dao.WatchlistDao;
import com.ezstock.model.WatchlistItem;
import com.ezstock.repository.WatchlistItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "jpa", matchIfMissing = true)
public class WatchlistJpaDao implements WatchlistDao {

    private static final Logger log = LoggerFactory.getLogger(WatchlistJpaDao.class);

    private final WatchlistItemRepository repository;

    public WatchlistJpaDao(WatchlistItemRepository repository) {
        this.repository = repository;
        log.info("WatchlistDao 使用 JPA 實作");
    }

    @Override
    public List<WatchlistItem> findByMember(long memberId) {
        List<WatchlistItem> items = repository.findByMember(memberId);
        log.debug("[JPA] 查詢自選股 會員 id={} 共 {} 檔", memberId, items.size());
        return items;
    }

    @Override
    @Transactional
    public boolean add(WatchlistItem item) {
        if (repository.existsById(item.getId())) {
            log.debug("[JPA] 自選股已存在 會員 id={} {}", item.getMemberId(), item.getId().getStockId());
            return false;
        }
        repository.save(item);
        log.debug("[JPA] 新增自選股 會員 id={} {}", item.getMemberId(), item.getId().getStockId());
        return true;
    }

    @Override
    @Transactional
    public boolean remove(long memberId, String stockId) {
        WatchlistItem.Key key = new WatchlistItem.Key(memberId, stockId);
        if (!repository.existsById(key)) {
            return false;
        }
        repository.deleteById(key);
        log.debug("[JPA] 移除自選股 會員 id={} {}", memberId, stockId);
        return true;
    }
}
