package com.ezstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "dividend")
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "cash_dividend", nullable = false, precision = 10, scale = 4)
    private BigDecimal cashDividend;

    protected Dividend() {
    }

    public Dividend(Stock stock, LocalDate exDate, BigDecimal cashDividend) {
        this.stock = stock;
        this.exDate = exDate;
        this.cashDividend = cashDividend;
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public LocalDate getExDate() { return exDate; }
    public BigDecimal getCashDividend() { return cashDividend; }
}
