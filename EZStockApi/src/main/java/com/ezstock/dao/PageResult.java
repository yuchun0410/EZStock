package com.ezstock.dao;

import java.util.List;

/**
 * 分頁查詢的結果。
 *
 * @param items 這一頁的資料
 * @param total 符合條件的總筆數（算總頁數用）
 */
public record PageResult<T>(List<T> items, long total) {
}
