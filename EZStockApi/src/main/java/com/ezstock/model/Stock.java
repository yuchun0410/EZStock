package com.ezstock.model;

import jakarta.persistence.*;

@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockType type;

    @Column(length = 50)
    private String industry;

    protected Stock() {
    }

    public Stock(String stockId, String name, StockType type, String industry) {
        this.stockId = stockId;
        this.name = name;
        this.type = type;
        this.industry = industry;
    }

    public String getStockId() { return stockId; }
    public String getName() { return name; }
    public StockType getType() { return type; }
    public String getIndustry() { return industry; }

    public void setName(String name) { this.name = name; }
    public void setType(StockType type) { this.type = type; }
    public void setIndustry(String industry) { this.industry = industry; }
}