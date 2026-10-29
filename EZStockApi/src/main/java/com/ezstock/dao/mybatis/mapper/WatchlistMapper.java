package com.ezstock.dao.mybatis.mapper;

import com.ezstock.model.WatchlistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WatchlistMapper {

    List<WatchlistItem> findByMember(@Param("memberId") long memberId);

    /** INSERT IGNORE：主鍵重複就略過，回傳 0 */
    int insertIgnore(@Param("item") WatchlistItem item);

    int delete(@Param("memberId") long memberId, @Param("stockId") String stockId);
}
