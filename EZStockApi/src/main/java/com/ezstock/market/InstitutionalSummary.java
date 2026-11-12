package com.ezstock.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 某一天整體市場的三大法人買賣金額（元）。抓不到資料時 date 是 null、groups 是空的。
 *
 * @param groups 外資、投信、自營商三類
 * @param total  三大法人合計
 */
public record InstitutionalSummary(LocalDate date, List<Group> groups, Group total) {

    /** @param net 買賣超 = 買進 − 賣出，正的是買超、負的是賣超 */
    public record Group(String name, BigDecimal buy, BigDecimal sell, BigDecimal net) {
    }
}
