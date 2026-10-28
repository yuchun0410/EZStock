package com.ezstock.dto;

import com.ezstock.backtest.strategy.ConditionType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 表單上的一條條件，例如 {"type": "CLOSE_BELOW_LOWEST_LOW", "value": 5}。
 * 這裡只檢查有沒有填；數值範圍（例如天數 1～240）由 ConditionType 檢查，規則才不會分散在兩個地方。
 */
public record ConditionSpec(
        @NotNull(message = "請選擇條件種類") ConditionType type,
        @NotNull(message = "請輸入條件的數值") BigDecimal value) {
}
