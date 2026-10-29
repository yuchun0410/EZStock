package com.ezstock.dao.mybatis;

import com.ezstock.dao.WatchlistDao;
import com.ezstock.dao.mybatis.mapper.WatchlistMapper;
import com.ezstock.model.WatchlistItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "ezstock.dao.type", havingValue = "mybatis")
public class WatchlistMyBatisDao implements WatchlistDao {

    private static final Logger log = LoggerFactory.getLogger(WatchlistMyBatisDao.class);

    private final WatchlistMapper mapper;

    public WatchlistMyBatisDao(WatchlistMapper mapper) {
        this.mapper = mapper;
        log.info("WatchlistDao 使用 MyBatis 實作");
    }

    @Override
    public List<WatchlistItem> findByMember(long memberId) {
        List<WatchlistItem> items = mapper.findByMember(memberId);
        log.debug("[MyBatis] 查詢自選股 會員 id={} 共 {} 檔", memberId, items.size());
        return items;
    }

    @Override
    @Transactional
    public boolean add(WatchlistItem item) {
        boolean added = mapper.insertIgnore(item) > 0;
        log.debug("[MyBatis] {}自選股 會員 id={} {}", added ? "新增" : "已存在，略過 ", item.getMemberId(),
                item.getId().getStockId());
        return added;
    }

    @Override
    @Transactional
    public boolean remove(long memberId, String stockId) {
        boolean removed = mapper.delete(memberId, stockId) > 0;
        log.debug("[MyBatis] 移除自選股 會員 id={} {}：{}", memberId, stockId, removed ? "成功" : "本來就沒有");
        return removed;
    }
}
