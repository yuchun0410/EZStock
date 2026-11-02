package com.ezstock.service.impl;

import com.ezstock.dao.StockDao;
import com.ezstock.dao.WatchlistDao;
import com.ezstock.dto.WatchlistItemResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.Stock;
import com.ezstock.model.WatchlistItem;
import com.ezstock.service.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WatchlistServiceImpl implements WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistServiceImpl.class);

    private final WatchlistDao watchlistDao;
    private final StockDao stockDao;
    private final Clock clock;

    public WatchlistServiceImpl(WatchlistDao watchlistDao, StockDao stockDao, Clock clock) {
        this.watchlistDao = watchlistDao;
        this.stockDao = stockDao;
        this.clock = clock;
    }

    @Override
    public List<WatchlistItemResponse> list(long memberId) {
        return toResponse(watchlistDao.findByMember(memberId));
    }

    @Override
    public List<WatchlistItemResponse> add(long memberId, String stockId) {
        String id = stockId.trim().toUpperCase();
        Stock stock = stockDao.findById(id).orElseThrow(() -> new ResourceNotFoundException("查無股票 " + id));

        List<WatchlistItem> current = watchlistDao.findByMember(memberId);
        if (stockIds(current).contains(id)) {
            return toResponse(current);
        }
        if (current.size() >= MAX_ITEMS) {
            log.info("[自選股] 會員 id={} 已達上限 {} 檔，無法加入 {}", memberId, MAX_ITEMS, id);
            throw new BadRequestException("自選股最多 " + MAX_ITEMS + " 檔，請先移除一些");
        }
        watchlistDao.add(new WatchlistItem(memberId, stock, LocalDateTime.now(clock)));
        log.info("[自選股] 會員 id={} 加入 {} {}，共 {} 檔", memberId, id, stock.getName(), current.size() + 1);
        return list(memberId);
    }

    @Override
    public List<WatchlistItemResponse> remove(long memberId, String stockId) {
        String id = stockId.trim().toUpperCase();
        boolean removed = watchlistDao.remove(memberId, id);
        log.info("[自選股] 會員 id={} 移除 {}：{}", memberId, id, removed ? "成功" : "本來就沒有");
        return list(memberId);
    }

    @Override
    public List<WatchlistItemResponse> importItems(long memberId, List<String> stockIds) {
        Set<String> existing = stockIds(watchlistDao.findByMember(memberId));
        int capacity = MAX_ITEMS - existing.size();
        int added = 0;
        int duplicated = 0;
        int unknown = 0;
        int overLimit = 0;
        LocalDateTime now = LocalDateTime.now(clock);

        // 去掉重複、保留原本順序；同一批用遞增的時間，匯入後順序跟瀏覽器裡一樣
        for (String raw : new LinkedHashSet<>(stockIds.stream().map(s -> s.trim().toUpperCase()).toList())) {
            if (existing.contains(raw)) {
                duplicated++;
                continue;
            }
            if (added >= capacity) {
                overLimit++;
                continue;
            }
            Optional<Stock> stock = stockDao.findById(raw);
            if (stock.isEmpty()) {
                unknown++;
                continue;
            }
            if (watchlistDao.add(new WatchlistItem(memberId, stock.get(), now.plusNanos(added * 1_000_000L)))) {
                added++;
            }
        }
        log.info("[自選股] 會員 id={} 匯入瀏覽器自選股：新增 {}、已存在 {}、查無股票 {}、超過上限 {}",
                memberId, added, duplicated, unknown, overLimit);
        return list(memberId);
    }

    private static Set<String> stockIds(List<WatchlistItem> items) {
        return items.stream().map(item -> item.getId().getStockId()).collect(Collectors.toSet());
    }

    private static List<WatchlistItemResponse> toResponse(List<WatchlistItem> items) {
        return items.stream().map(WatchlistItemResponse::from).toList();
    }
}
