package com.ezstock.backtest.strategy;

import com.ezstock.model.DailyPrice;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 測試用：快速做出一串 K 棒 */
public final class TestBars {

    public static final Stock STOCK = new Stock("TEST", "測試股", StockType.STOCK, "測試");
    public static final LocalDate DAY1 = LocalDate.of(2025, 1, 2);

    private TestBars() {
    }

    /** 每天開高低收都一樣，只看收盤 */
    public static BarSeries closes(String... closes) {
        List<DailyPrice> list = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            BigDecimal c = new BigDecimal(closes[i]);
            list.add(new DailyPrice(STOCK, DAY1.plusDays(i), c, c, c, c, 1_000L));
        }
        return new BarSeries(list);
    }

    /** 指定每天的開盤、最高、最低、收盤，格式 "開,高,低,收" */
    public static BarSeries ohlc(String... rows) {
        List<DailyPrice> list = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            String[] v = rows[i].split(",");
            list.add(new DailyPrice(STOCK, DAY1.plusDays(i), new BigDecimal(v[0]), new BigDecimal(v[1]),
                    new BigDecimal(v[2]), new BigDecimal(v[3]), 1_000L));
        }
        return new BarSeries(list);
    }

    public static List<DailyPrice> ohlcList(String... rows) {
        BarSeries series = ohlc(rows);
        List<DailyPrice> list = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            list.add(series.bar(i));
        }
        return list;
    }
}
