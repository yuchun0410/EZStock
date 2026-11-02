package com.ezstock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 新增或修改一檔持股。
 *
 * @param shares      股數（1 張 = 1000 股）
 * @param averageCost 每股平均成本（建議含手續費）
 */
public record HoldingRequest(
        @NotNull(message = "請輸入股數")
        @Min(value = 1, message = "股數至少 1 股")
        @Max(value = 1_000_000_000L, message = "股數太大")
        Long shares,

        @NotNull(message = "請輸入平均成本")
        @DecimalMin(value = "0", inclusive = false, message = "平均成本要大於 0")
        @Digits(integer = 8, fraction = 4, message = "平均成本最多 8 位整數、4 位小數")
        BigDecimal averageCost) {
}
