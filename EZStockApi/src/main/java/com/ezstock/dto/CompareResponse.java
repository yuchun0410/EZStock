package com.ezstock.dto;

import com.ezstock.backtest.HoldingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 標的比較的結果。
 *
 * @param currency    金額的幣別（TWD / USD），同一次比較只會有一種
 * @param results     每一檔的結果，順序跟請求一樣
 * @param assumptions 這次回測用了哪些假設，畫面上要顯示給使用者看
 */
public record CompareResponse(LocalDate startDate, LocalDate endDate, BigDecimal initialCapital,
                              String currency, boolean reinvestDividends, String costDescription,
                              List<HoldingResult> results, List<String> assumptions) {
}
