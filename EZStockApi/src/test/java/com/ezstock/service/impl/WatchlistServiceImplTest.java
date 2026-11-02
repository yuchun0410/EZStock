package com.ezstock.service.impl;

import com.ezstock.dao.StockDao;
import com.ezstock.dao.WatchlistDao;
import com.ezstock.dto.WatchlistItemResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import com.ezstock.model.WatchlistItem;
import com.ezstock.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用一個 List 假裝是資料庫，測 Service 的規則。
 */
class WatchlistServiceImplTest {

    private static final long MEMBER = 7L;

    private final List<WatchlistItem> table = new ArrayList<>();
    private WatchlistDao watchlistDao;
    private StockDao stockDao;
    private WatchlistServiceImpl service;

    @BeforeEach
    void setUp() {
        watchlistDao = mock(WatchlistDao.class);
        stockDao = mock(StockDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T08:00:00Z"), ZoneId.of("Asia/Taipei"));
        service = new WatchlistServiceImpl(watchlistDao, stockDao, clock);

        when(watchlistDao.findByMember(MEMBER)).thenAnswer(invocation -> List.copyOf(table));
        when(watchlistDao.add(any())).thenAnswer(invocation -> {
            WatchlistItem item = invocation.getArgument(0);
            boolean exists = table.stream().anyMatch(i -> i.getId().equals(item.getId()));
            if (!exists) {
                table.add(item);
            }
            return !exists;
        });
        when(stockDao.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return id.startsWith("99") ? Optional.empty() : Optional.of(new Stock(id, "股票" + id, StockType.STOCK, "測試"));
        });
    }

    @Test
    @DisplayName("加入：代號去空白轉大寫，重複加入不會變兩筆")
    void add() {
        service.add(MEMBER, " 00631l ");
        List<WatchlistItemResponse> result = service.add(MEMBER, "00631L");

        assertThat(result).extracting(WatchlistItemResponse::stockId).containsExactly("00631L");
        assertThat(result.get(0).addedAt()).isEqualTo(LocalDateTime.of(2026, 10, 29, 16, 0));
    }

    @Test
    @DisplayName("加入：查無股票回 404")
    void unknownStock() {
        assertThatThrownBy(() -> service.add(MEMBER, "9999")).isInstanceOf(ResourceNotFoundException.class);
        verify(watchlistDao, never()).add(any());
    }

    @Test
    @DisplayName("加入：已經 50 檔就不能再加，但已經在清單裡的再加一次不算錯")
    void limit() {
        for (int i = 0; i < WatchlistService.MAX_ITEMS; i++) {
            service.add(MEMBER, String.valueOf(1000 + i));
        }

        assertThatThrownBy(() -> service.add(MEMBER, "2330")).isInstanceOf(BadRequestException.class);
        assertThat(service.add(MEMBER, "1000")).hasSize(WatchlistService.MAX_ITEMS);
    }

    @Test
    @DisplayName("匯入：已存在、重複、查無的都略過，其他依原本順序加入")
    void importItems() {
        service.add(MEMBER, "2330");

        List<WatchlistItemResponse> result = service.importItems(MEMBER,
                List.of("0050", "2330", "9999", "0050", "2317"));

        assertThat(result).extracting(WatchlistItemResponse::stockId).containsExactly("2330", "0050", "2317");
    }

    @Test
    @DisplayName("匯入：超過上限的部分略過，不會整批失敗")
    void importOverLimit() {
        for (int i = 0; i < WatchlistService.MAX_ITEMS - 1; i++) {
            service.add(MEMBER, String.valueOf(1000 + i));
        }

        List<WatchlistItemResponse> result = service.importItems(MEMBER, List.of("2330", "2317", "0050"));

        assertThat(result).hasSize(WatchlistService.MAX_ITEMS);
        assertThat(result.get(WatchlistService.MAX_ITEMS - 1).stockId()).isEqualTo("2330");
    }
}
