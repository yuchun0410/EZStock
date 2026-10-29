package com.ezstock.backtest.strategy;

import java.math.BigDecimal;

/**
 * 目前持有中的部位，給「賣出條件」判斷用（例如停利要知道買進價、持有天數要知道哪天買的）。
 *
 * @param entryIndex 買進那天是第幾根 K 棒
 * @param entryPrice 買進價
 */
public record OpenPosition(int entryIndex, BigDecimal entryPrice) {
}
