package com.ezstock.model;

import jakarta.persistence.*;

@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Market market = Market.TW;

    @Column(length = 100)
    private String industry;

    protected Stock() {
    }

    /** 台股 */
    public Stock(String stockId, String name, StockType type, String industry) {
        this(stockId, name, type, Market.TW, industry);
    }

    public Stock(String stockId, String name, StockType type, Market market, String industry) {
        this.stockId = stockId;
        this.name = name;
        this.type = type;
        this.market = market;
        this.industry = industry;
    }

    public String getStockId() { return stockId; }
    public String getName() { return name; }
    public StockType getType() { return type; }
    public Market getMarket() { return market; }
    public String getIndustry() { return industry; }
    public boolean isUs() { return market == Market.US; }

    public void setName(String name) { this.name = name; }
    public void setType(StockType type) { this.type = type; }
    public void setIndustry(String industry) { this.industry = industry; }
    public void setMarket(Market market) { this.market = market; }
}