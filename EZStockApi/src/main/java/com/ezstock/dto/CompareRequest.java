package com.ezstock.dto;

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
 * 標的比較的請求：同一筆錢、同一天買進這幾檔，一直持有到結束日。
 *
 * @param stockIds          要比較的標的，1～5 檔
 * @param startDate         起始日，會在這天之後的第一個交易日買進
 * @param endDate           結束日，不給就是今天
 * @param initialCapital    每一檔各投入多少錢
 * @param reinvestDividends 股利是否再投入，不給預設 true
 * @param includeCosts      是否計算手續費，不給預設 true
 * @param feeDiscount       手續費折扣（0.6 = 6 折），不給就是不打折
 */
public record CompareRequest(
        @NotEmpty(message = "請至少選擇 1 檔標的")
        @Size(max = 5, message = "最多比較 5 檔標的")
        List<@NotBlank(message = "股票代號不能是空白") String> stockIds,

        @NotNull(message = "請選擇起始日")
        @PastOrPresent(message = "起始日不能是未來")
        LocalDate startDate,

        LocalDate endDate,

        @NotNull(message = "請輸入投入金額")
        @DecimalMin(value = "1000", message = "投入金額至少 1,000 元")
        @DecimalMax(value = "100000000", message = "投入金額最多 1 億元")
        BigDecimal initialCapital,

        Boolean reinvestDividends,

        Boolean includeCosts,

        @DecimalMin(value = "0.1", message = "手續費折扣最低 1 折")
        @DecimalMax(value = "1", message = "手續費折扣不能超過 1（不打折）")
        BigDecimal feeDiscount) {
}
