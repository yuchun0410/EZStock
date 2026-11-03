package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.PriceAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PriceAlertMapper {

    List<PriceAlert> findActiveByMember(@Param("memberId") long memberId);

    int insert(PriceAlert alert);

    /** 提醒建立後只會改「通知時間」 */
    int updateTriggeredAt(PriceAlert alert);

    int deleteByIdAndMember(@Param("id") long id, @Param("memberId") long memberId);
}
