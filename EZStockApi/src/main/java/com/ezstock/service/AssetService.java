package com.ezstock.service;

import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dto.HoldingRequest;
import com.ezstock.dto.MoneyAccountRequest;

/**
 * 資產儀表板。所有修改都回傳最新的儀表板，前端直接用它更新畫面。
 */
public interface AssetService {

    int MAX_ACCOUNTS = 50;
    int MAX_HOLDINGS = 100;

    AssetSummaryResponse summary(long memberId);

    AssetSummaryResponse createAccount(long memberId, MoneyAccountRequest request);

    AssetSummaryResponse updateAccount(long memberId, long accountId, MoneyAccountRequest request);

    AssetSummaryResponse deleteAccount(long memberId, long accountId);

    /** 新增或修改一檔持股（同一檔只會有一筆） */
    AssetSummaryResponse saveHolding(long memberId, String stockId, HoldingRequest request);

    AssetSummaryResponse deleteHolding(long memberId, String stockId);
}
