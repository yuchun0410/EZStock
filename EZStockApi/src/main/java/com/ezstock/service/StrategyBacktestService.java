package com.ezstock.service;

import com.ezstock.dto.ConditionTypeInfo;
import com.ezstock.dto.StrategyRequest;
import com.ezstock.dto.StrategyResponse;

import java.util.List;

public interface StrategyBacktestService {

    /** 可以選的條件種類（給前端畫表單） */
    List<ConditionTypeInfo> conditionTypes();

    /** 條件回測：照條件買賣，並跟「一直持有」比較 */
    StrategyResponse run(StrategyRequest request);
}
