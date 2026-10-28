package com.ezstock.model;

/** 會讓股數改變的公司行為 */
public enum SplitType {
    /** 股票分割，例如 1 股拆成 4 股 */
    SPLIT,
    /** 面額變更，例如面額 10 元改成 1 元，1 股變成 10 股 */
    PAR_VALUE_CHANGE
}
