package com.ezstock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 條件回測的請求。
 *
 * @param entryConditions 買進條件，全部成立才買（1～3 個）
 * @param exitConditions  賣出條件，任一個成立就賣（1～3 個）
 * @param reinvestDividends 股利是否再投入（條件策略和對照組一致），不給預設 true
 */
public record StrategyRequest(
        @NotBlank(message = "請選擇股票")
        String stockId,

        @NotNull(message = "請選擇起始日")
        @PastOrPresent(message = "起始日不能是未來")
        LocalDate startDate,

        LocalDate endDate,

        @NotNull(message = "請輸入投入金額")
        @DecimalMin(value = "1000", message = "投入金額至少 1,000 元")
        @DecimalMax(value = "100000000", message = "投入金額最多 1 億元")
        BigDecimal initialCapital,

        @NotEmpty(message = "請至少設定 1 個買進條件")
        @Size(max = 3, message = "買進條件最多 3 個")
        List<@Valid ConditionSpec> entryConditions,

        @NotEmpty(message = "請至少設定 1 個賣出條件")
        @Size(max = 3, message = "賣出條件最多 3 個")
        List<@Valid ConditionSpec> exitConditions,

        Boolean reinvestDividends,

        Boolean includeCosts,

        @DecimalMin(value = "0.1", message = "手續費折扣最低 1 折")
        @DecimalMax(value = "1", message = "手續費折扣不能超過 1（不打折）")
        BigDecimal feeDiscount) {
}
