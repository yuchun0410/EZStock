package com.ezstock.backtest.cost;

import com.ezstock.model.StockType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 台股的交易成本：
 * <ul>
 *   <li>手續費：成交金額 × 0.1425% × 折扣，買賣都要付，不足 20 元以 20 元計</li>
 *   <li>證交稅：只有賣出要付，股票 0.3%、ETF 0.1%</li>
 * </ul>
 * 券商實際收費是「元」為單位、小數無條件捨去，這裡也照做。
 */
public class TaiwanStockCostModel implements CostModel {

    public static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
    public static final BigDecimal MIN_FEE = new BigDecimal("20");
    public static final BigDecimal STOCK_TAX_RATE = new BigDecimal("0.003");
    public static final BigDecimal ETF_TAX_RATE = new BigDecimal("0.001");

    private final BigDecimal feeDiscount;

    /** 手續費不打折 */
    public TaiwanStockCostModel() {
        this(BigDecimal.ONE);
    }

    /**
     * @param feeDiscount 手續費折扣，例如 0.6 代表 6 折；必須大於 0 且不超過 1
     */
    public TaiwanStockCostModel(BigDecimal feeDiscount) {
        if (feeDiscount == null || feeDiscount.signum() <= 0 || feeDiscount.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("手續費折扣必須大於 0 且不超過 1：" + feeDiscount);
        }
        this.feeDiscount = feeDiscount;
    }

    @Override
    public BigDecimal buyCost(BigDecimal amount, StockType type) {
        return fee(amount);
    }

    @Override
    public BigDecimal sellCost(BigDecimal amount, StockType type) {
        return fee(amount).add(tax(amount, type));
    }

    @Override
    public String describe() {
        String discount = feeDiscount.compareTo(BigDecimal.ONE) == 0
                ? ""
                : " × " + feeDiscount.movePointRight(1).stripTrailingZeros().toPlainString() + " 折";
        return "手續費 0.1425%" + discount + "，最低 20 元；證交稅 股票 0.3%、ETF 0.1%（賣出時）";
    }

    private BigDecimal fee(BigDecimal amount) {
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = amount.multiply(FEE_RATE).multiply(feeDiscount).setScale(0, RoundingMode.DOWN);
        return fee.max(MIN_FEE);
    }

    private BigDecimal tax(BigDecimal amount, StockType type) {
        BigDecimal rate = type == StockType.ETF ? ETF_TAX_RATE : STOCK_TAX_RATE;
        return amount.multiply(rate).setScale(0, RoundingMode.DOWN);
    }
}
