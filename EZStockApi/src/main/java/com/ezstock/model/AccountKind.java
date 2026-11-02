package com.ezstock.model;

/** 手動記錄的帳戶種類 */
public enum AccountKind {

    /** 現金：銀行存款、現金，算進資產 */
    CASH("現金"),

    /** 負債：信貸、房貸、信用卡，淨資產要扣掉 */
    LIABILITY("負債");

    private final String label;

    AccountKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
