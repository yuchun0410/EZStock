package com.ezstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_price")
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private Long volume;

    protected DailyPrice() {
    }

    public DailyPrice(Stock stock, LocalDate tradeDate,
                      BigDecimal openPrice, BigDecimal highPrice,
                      BigDecimal lowPrice, BigDecimal closePrice, Long volume) {
        this.stock = stock;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public LocalDate getTradeDate() { return tradeDate; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public Long getVolume() { return volume; }
}