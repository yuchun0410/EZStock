package com.ezstock.service;

/** 文章列表的排序：熱門（熱度分數高的在前）或最新（發文時間新的在前） */
public enum PostSort {
    HOT,
    LATEST;

    /** 網址參數 ?sort=hot / latest，大小寫都可以；沒給或看不懂就用最新 */
    public static PostSort parse(String value) {
        if (value == null) {
            return LATEST;
        }
        return switch (value.trim().toLowerCase()) {
            case "hot" -> HOT;
            default -> LATEST;
        };
    }
}
