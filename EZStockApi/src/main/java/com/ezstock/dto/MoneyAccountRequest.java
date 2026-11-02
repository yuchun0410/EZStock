package com.ezstock.dto;

import com.ezstock.model.AccountKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新增或修改現金、負債帳戶。
 *
 * @param kind    只有新增時需要；修改時不能改種類，給了也會忽略
 * @param balance 一律填正數，負債也是填「還欠多少」
 */
public record MoneyAccountRequest(
        AccountKind kind,

        @NotBlank(message = "請輸入名稱")
        @Size(max = 50, message = "名稱最多 50 個字")
        String name,

        @NotNull(message = "請輸入金額")
        @DecimalMin(value = "0", message = "金額不能是負數")
        @Digits(integer = 14, fraction = 2, message = "金額最多 14 位整數、2 位小數")
        BigDecimal balance) {
}
