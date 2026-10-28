package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.Dividend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DividendMapper {

    List<Dividend> findByStockAndDateRange(@Param("stockId") String stockId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    int insertBatch(@Param("dividends") List<Dividend> dividends);
}
