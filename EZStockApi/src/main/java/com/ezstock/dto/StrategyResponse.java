package com.ezstock.dto;

import com.ezstock.backtest.HoldingResult;
import com.ezstock.backtest.strategy.StrategyResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 條件回測的結果。
 *
 * @param strategy    照條件買賣的結果
 * @param benchmark   對照組：同一段期間「第一天買進、一直持有」的結果，看條件有沒有比較好
 * @param assumptions 回測假設，畫面上要顯示
 */
public record StrategyResponse(String stockId, String name,
                               LocalDate startDate, LocalDate endDate, BigDecimal initialCapital,
                               boolean reinvestDividends,
                               String entryDescription, String exitDescription, String costDescription,
                               StrategyResult strategy, HoldingResult benchmark,
                               List<String> assumptions) {
}
