package com.ezstock.service;

import com.ezstock.dto.CompareRequest;
import com.ezstock.dto.CompareResponse;

public interface BacktestService {

    /** 標的比較：同一筆錢、同一天買進每一檔，持有到結束日 */
    CompareResponse compare(CompareRequest request);
}
