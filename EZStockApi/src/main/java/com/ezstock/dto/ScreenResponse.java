package com.ezstock.dto;

import java.util.List;

/**
 * 條件選股的結果。
 *
 * @param conditionDescription 條件的說明，例如「收盤站上 20 日線（月線）」
 * @param scannedCount         實際判斷了幾檔
 * @param matches              符合條件的股票，依代號排序
 * @param skipped              無法判斷的股票與原因（例如資料不足、外部資料來源失敗）
 */
public record ScreenResponse(String conditionDescription, int scannedCount,
                             List<ScreenMatch> matches, List<String> skipped) {
}
