package com.ezstock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 條件選股的請求。
 *
 * @param stockIds   要掃描哪些股票（例如自選股）；不給或空的，就掃描資料庫裡所有有價格的股票
 * @param conditions 選股條件，全部成立才算符合（跟條件回測的買進條件一樣）
 */
public record ScreenRequest(
        @Size(max = 100, message = "一次最多掃描 100 檔")
        List<String> stockIds,

        @NotEmpty(message = "請至少設定 1 個條件")
        @Size(max = 3, message = "條件最多 3 個")
        List<@Valid ConditionSpec> conditions) {
}
