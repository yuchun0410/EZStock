package com.ezstock.dao;

import com.ezstock.model.PriceAlert;

import java.util.List;

/**
 * 到價提醒的存取介面。
 */
public interface PriceAlertDao {

    /** 某個會員還沒通知過的提醒（含股票名稱），依建立順序 */
    List<PriceAlert> findActiveByMember(long memberId);

    PriceAlert save(PriceAlert alert);

    /** 只刪得到自己的；回傳是否真的有刪到 */
    boolean deleteByIdAndMember(long id, long memberId);
}
