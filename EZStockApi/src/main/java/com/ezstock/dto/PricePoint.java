package com.ezstock.dto;

import com.ezstock.model.DailyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 走勢圖的一個點（一天的開高低收量） */
public record PricePoint(LocalDate date,
                         BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                         long volume) {

    public static PricePoint from(DailyPrice price) {
        return new PricePoint(price.getTradeDate(),
                price.getOpenPrice(), price.getHighPrice(), price.getLowPrice(), price.getClosePrice(),
                price.getVolume());
    }
}
