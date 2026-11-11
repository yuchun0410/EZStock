package com.ezstock.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 新增或修改一檔持股。
 *
 * @param shares      股數。台股要是整數（1 張 = 1000 股）；美股可以是碎股，最多小數點後 5 位
 * @param averageCost 每股平均成本（建議含手續費）
 */
public record HoldingRequest(
        @NotNull(message = "請輸入股數")
        @DecimalMin(value = "0.00001", message = "股數至少 0.00001 股")
        @DecimalMax(value = "1000000000", message = "股數太大")
        @Digits(integer = 10, fraction = 5, message = "股數最多小數點後 5 位")
        BigDecimal shares,

        @NotNull(message = "請輸入平均成本")
        @DecimalMin(value = "0", inclusive = false, message = "平均成本要大於 0")
        @Digits(integer = 8, fraction = 4, message = "平均成本最多 8 位整數、4 位小數")
        BigDecimal averageCost) {
}
