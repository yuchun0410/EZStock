package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.StockSplit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockSplitMapper {

    List<StockSplit> findByStockAndDateRange(@Param("stockId") String stockId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    int insertBatch(@Param("splits") List<StockSplit> splits);
}
