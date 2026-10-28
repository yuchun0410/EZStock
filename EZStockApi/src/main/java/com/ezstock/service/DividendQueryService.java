package com.ezstock.service;

import com.ezstock.dto.DividendRecord;

import java.util.List;

public interface DividendQueryService {

    /** 最近 10 年的除息紀錄，依除息日由新到舊；查無股票丟出 ResourceNotFoundException */
    List<DividendRecord> getDividends(String stockId);
}
