package com.ezstock.service;

import java.util.Optional;

/** 取得最新匯率，資產儀表板把美股換算成台幣用 */
public interface ExchangeRateService {

    /** 最新的美元兌台幣匯率；外部來源抓不到、也沒有舊的可以用時回傳 empty */
    Optional<FxRate> latestUsdTwd();
}
