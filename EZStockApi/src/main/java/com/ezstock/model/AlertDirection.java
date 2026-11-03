package com.ezstock.model;

import java.math.BigDecimal;

/** 到價提醒的方向 */
public enum AlertDirection {

    /** 收盤價 >= 目標價 */
    ABOVE("突破", ">"),

    /** 收盤價 <= 目標價 */
    BELOW("跌破", "<");

    private final String label;
    private final String symbol;

    AlertDirection(String label, String symbol) {
        this.label = label;
        this.symbol = symbol;
    }

    public String label() {
        return label;
    }

    public String symbol() {
        return symbol;
    }

    public boolean isReached(BigDecimal close, BigDecimal target) {
        int compare = close.compareTo(target);
        return this == ABOVE ? compare >= 0 : compare <= 0;
    }

    public static AlertDirection fromSymbol(String symbol) {
        return switch (symbol) {
            case ">" -> ABOVE;
            case "<" -> BELOW;
            default -> throw new IllegalArgumentException("方向只能是 > 或 <：" + symbol);
        };
    }
}
