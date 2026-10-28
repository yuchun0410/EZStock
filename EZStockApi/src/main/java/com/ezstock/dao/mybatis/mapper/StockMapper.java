package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockMapper {

    Stock findById(@Param("stockId") String stockId);

    List<Stock> search(@Param("keyword") String keyword, @Param("limit") int limit);

    List<Stock> findAll();

    int upsert(Stock stock);
}
