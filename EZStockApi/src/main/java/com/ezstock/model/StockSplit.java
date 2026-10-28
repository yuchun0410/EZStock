package com.ezstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 股票分割或面額變更事件。
 * 持有人的股數會乘上 {@link #shareRatio()}，價格同時除以同一個比例，總價值不變。
 */
@Entity
@Table(name = "stock_split")
public class StockSplit {

    /** 比例跟整數（或 1/整數）差不到 1%，就當成剛好是那個整數，避免 3.9998 股這種尾差 */
    private static final BigDecimal SNAP_TOLERANCE = new BigDecimal("0.01");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "split_date", nullable = false)
    private LocalDate splitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Column(name = "before_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal beforePrice;

    @Column(name = "after_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal afterPrice;

    protected StockSplit() {
    }

    public StockSplit(Stock stock, LocalDate splitDate, SplitType splitType,
                      BigDecimal beforePrice, BigDecimal afterPrice) {
        this.stock = stock;
        this.splitDate = splitDate;
        this.splitType = splitType;
        this.beforePrice = beforePrice;
        this.afterPrice = afterPrice;
    }

    /**
     * 股數要乘上的比例 = 事件前價格 / 事件後價格。
     * 例如 188.65 → 47.16，比例約 4.0002，修正成剛好 4（1 拆 4）。
     * 反分割（例如 2 股合成 1 股）比例會小於 1，修正成 1/2 = 0.5。
     */
    public BigDecimal shareRatio() {
        return snapToSimpleRatio(beforePrice.divide(afterPrice, 6, RoundingMode.HALF_UP));
    }

    static BigDecimal snapToSimpleRatio(BigDecimal raw) {
        if (raw.compareTo(BigDecimal.ONE) >= 0) {
            BigDecimal whole = raw.setScale(0, RoundingMode.HALF_UP);
            return isClose(raw, whole) ? whole : raw;
        }
        BigDecimal inverse = BigDecimal.ONE.divide(raw, 6, RoundingMode.HALF_UP);
        BigDecimal whole = inverse.setScale(0, RoundingMode.HALF_UP);
        return isClose(inverse, whole) ? BigDecimal.ONE.divide(whole, 6, RoundingMode.HALF_UP) : raw;
    }

    private static boolean isClose(BigDecimal value, BigDecimal target) {
        return target.signum() > 0
                && value.subtract(target).abs().divide(target, 6, RoundingMode.HALF_UP).compareTo(SNAP_TOLERANCE) < 0;
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public LocalDate getSplitDate() { return splitDate; }
    public SplitType getSplitType() { return splitType; }
    public BigDecimal getBeforePrice() { return beforePrice; }
    public BigDecimal getAfterPrice() { return afterPrice; }
}
