package com.ezstock.dto;

import com.ezstock.model.AccountKind;
import com.ezstock.model.MoneyAccount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MoneyAccountResponse(Long id, AccountKind kind, String name, BigDecimal balance,
                                   LocalDateTime updatedAt) {

    public static MoneyAccountResponse from(MoneyAccount account) {
        return new MoneyAccountResponse(account.getId(), account.getKind(), account.getName(),
                account.getBalance(), account.getUpdatedAt());
    }
}
