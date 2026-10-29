package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 把瀏覽器裡（沒登入時加的）自選股匯入帳號。
 */
public record WatchlistImportRequest(
        @NotNull(message = "缺少要匯入的股票")
        @Size(max = 100, message = "一次最多匯入 100 檔")
        List<@NotBlank String> stockIds) {
}
