package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.DailyPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyPriceMapper {

    List<DailyPrice> findByStockAndDateRange(@Param("stockId") String stockId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    DailyPrice findLatest(@Param("stockId") String stockId);

    List<LocalDate> findTradeDates(@Param("stockId") String stockId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    int insertBatch(@Param("prices") List<DailyPrice> prices);
}
