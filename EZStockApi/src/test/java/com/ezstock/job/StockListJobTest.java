package com.ezstock.job;

import com.ezstock.dao.StockDao;
import com.ezstock.model.Market;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import com.ezstock.service.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockListJobTest {

    private StockDao stockDao;
    private ImportService importService;
    private StockListJob job;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        importService = mock(ImportService.class);
        job = new StockListJob(stockDao, importService);
    }

    @Test
    @DisplayName("啟動：一定更新台股清單；已經有美股就不重新匯入美股清單")
    void startupWithUsStocks() {
        when(stockDao.findAll()).thenReturn(List.of(
                new Stock("2330", "台積電", StockType.STOCK, "半導體業"),
                new Stock("AAPL", "Apple Inc.", StockType.STOCK, Market.US, "Computer Manufacturing")));

        job.run(null);

        verify(importService).importTwStockList();
        verify(importService, never()).importUsStockList();
    }

    @Test
    @DisplayName("啟動：還沒有美股就匯入一次美股清單")
    void startupWithoutUsStocks() {
        when(stockDao.findAll()).thenReturn(List.of(new Stock("2330", "台積電", StockType.STOCK, "半導體業")));

        job.run(null);

        verify(importService).importTwStockList();
        verify(importService).importUsStockList();
    }

    @Test
    @DisplayName("每週更新：台股清單失敗也照樣更新美股清單，例外不往外丟")
    void weeklyRefreshContinuesAfterFailure() {
        when(importService.importTwStockList()).thenThrow(new IllegalStateException("FinMind 掛了"));

        assertThatCode(() -> job.refresh()).doesNotThrowAnyException();

        verify(importService).importUsStockList();
    }
}
