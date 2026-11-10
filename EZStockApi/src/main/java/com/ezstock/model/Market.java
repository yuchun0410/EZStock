package com.ezstock.model;

import java.time.LocalTime;

/**
 * 股票所在的市場。決定幣別、成交量單位、交易成本，以及外部資料大概幾點會更新。
 *
 * <ul>
 *   <li>台股：13:30 收盤，FinMind 下午更新，台灣時間 16:00 後預期有「今天」的資料</li>
 *   <li>美股：美東 16:00 收盤（台灣時間清晨 4、5 點），FinMind 約台灣時間 08:00 更新，
 *       所以台灣時間 08:00 後才預期有「美國前一個交易日」的資料</li>
 * </ul>
 *
 * @param currency       幣別代碼
 * @param dataReadyTime  台灣時間幾點之後，外部資料來源預期已經有最新一個交易日的資料
 * @param dataLagDays    最新交易日比台灣「今天」晚幾天（美股差一天）
 */
public enum Market {
    TW("TWD", LocalTime.of(16, 0), 0),
    US("USD", LocalTime.of(8, 0), 1);

    private final String currency;
    private final LocalTime dataReadyTime;
    private final int dataLagDays;

    Market(String currency, LocalTime dataReadyTime, int dataLagDays) {
        this.currency = currency;
        this.dataReadyTime = dataReadyTime;
        this.dataLagDays = dataLagDays;
    }

    /**
     * 從代號判斷市場：台股代號一定是數字開頭（2330、0050、00878），美股是英文字母（AAPL、BRK-B）。
     * 給只有代號、沒有查 Stock 的地方用（例如按需載入判斷資料該不該更新了）。
     */
    public static Market fromStockId(String stockId) {
        return stockId != null && !stockId.isEmpty() && Character.isDigit(stockId.charAt(0)) ? TW : US;
    }

    public String currency() { return currency; }
    public LocalTime dataReadyTime() { return dataReadyTime; }
    public int dataLagDays() { return dataLagDays; }
}
