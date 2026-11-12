package com.ezstock.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一個指數的最新行情與近期走勢。抓不到資料時，close 之後的欄位都是 null、points 是空的。
 *
 * @param change        漲跌（點），跟前一個交易日比
 * @param changePercent 漲跌幅（%）
 * @param points        近三個月每天的收盤，畫小走勢圖用
 */
public record IndexQuote(String code, String name, String note, String market,
                         LocalDate date, BigDecimal close, BigDecimal change, BigDecimal changePercent,
                         List<Point> points) {

    public record Point(LocalDate date, BigDecimal close) {
    }
}
