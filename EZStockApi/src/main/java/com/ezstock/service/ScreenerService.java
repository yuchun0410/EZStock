package com.ezstock.service;

import com.ezstock.dto.ScreenRequest;
import com.ezstock.dto.ScreenResponse;

public interface ScreenerService {

    /** 條件選股：用最新一天的收盤判斷，列出符合條件的股票 */
    ScreenResponse screen(ScreenRequest request);
}
