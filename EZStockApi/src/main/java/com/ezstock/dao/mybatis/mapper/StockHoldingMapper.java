package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.StockHolding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockHoldingMapper {

    List<StockHolding> findByMember(@Param("memberId") long memberId);

    /** INSERT ... ON DUPLICATE KEY UPDATE：一句 SQL 完成「有就更新、沒有就新增」 */
    int upsert(@Param("h") StockHolding holding);

    int delete(@Param("memberId") long memberId, @Param("stockId") String stockId);
}
