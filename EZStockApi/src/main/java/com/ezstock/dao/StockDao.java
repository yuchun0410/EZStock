package com.ezstock.dao;

import com.ezstock.model.Stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 股票基本資料的存取介面。
 * 實作有 JPA 與 MyBatis 兩種，由 application.properties 的 ezstock.dao.type 切換。
 */
public interface StockDao {

    Optional<Stock> findById(String stockId);

    /** 依代號或名稱模糊搜尋，依代號排序，最多回傳 limit 筆（由資料庫 LIMIT，不在 Java 裡截斷） */
    List<Stock> search(String keyword, int limit);

    List<Stock> findAll();

    /** 一次查好幾檔（例如論壇列表要顯示每篇文章標記的股票名稱），避免一檔一檔查 */
    List<Stock> findByIds(Collection<String> stockIds);

    /** 不存在就新增，存在就更新名稱、類型、產業 */
    void save(Stock stock);
}
