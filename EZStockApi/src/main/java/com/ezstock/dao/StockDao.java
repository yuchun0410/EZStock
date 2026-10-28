package com.ezstock.dao;

import com.ezstock.model.Stock;

import java.util.List;
import java.util.Optional;

/**
 * 股票基本資料的存取介面。
 * 實作有 JPA 與 MyBatis 兩種，由 application.properties 的 ezstock.dao.type 切換。
 */
public interface StockDao {

    Optional<Stock> findById(String stockId);

    /** 依代號或名稱模糊搜尋 */
    List<Stock> search(String keyword);

    List<Stock> findAll();

    /** 不存在就新增，存在就更新名稱、類型、產業 */
    void save(Stock stock);
}
