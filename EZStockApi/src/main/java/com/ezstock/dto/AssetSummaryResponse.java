package com.ezstock.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 資產儀表板。
 *
 * @param totalAssets  總資產 = 現金 + 股票現值
 * @param netWorth     淨資產 = 總資產 − 負債
 * @param stockCost    有股價的持股的總成本（沒有股價的不算，損益才算得準）
 * @param notes        給使用者看的提醒，例如某檔沒有股價
 */
public record AssetSummaryResponse(BigDecimal cashTotal, BigDecimal stockValue, BigDecimal totalAssets,
                                   BigDecimal liabilityTotal, BigDecimal netWorth,
                                   BigDecimal stockCost, BigDecimal stockUnrealizedPnl,
                                   BigDecimal stockReturnPercent,
                                   List<HoldingResponse> holdings, List<MoneyAccountResponse> accounts,
                                   List<String> notes) {
}
