package com.ezstock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 資產儀表板。
 *
 * 所有合計都是台幣；美股持股用最新匯率換算後加進去。
 *
 * @param totalAssets  總資產 = 現金 + 股票現值
 * @param netWorth     淨資產 = 總資產 − 負債
 * @param stockCost    有股價的持股的總成本（沒有股價的不算，損益才算得準）
 * @param notes        給使用者看的提醒，例如某檔沒有股價
 * @param usdTwdRate     換算美股用的匯率（1 美元 = ? 元）；沒有美股持股、或抓不到匯率時是 null
 * @param usdTwdRateDate 匯率的牌告日期
 */
public record AssetSummaryResponse(BigDecimal cashTotal, BigDecimal stockValue, BigDecimal totalAssets,
                                   BigDecimal liabilityTotal, BigDecimal netWorth,
                                   BigDecimal stockCost, BigDecimal stockUnrealizedPnl,
                                   BigDecimal stockReturnPercent,
                                   List<HoldingResponse> holdings, List<MoneyAccountResponse> accounts,
                                   List<String> notes, BigDecimal usdTwdRate, LocalDate usdTwdRateDate) {
}
