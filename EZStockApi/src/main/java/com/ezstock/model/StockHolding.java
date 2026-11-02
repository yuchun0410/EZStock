package com.ezstock.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 會員持有的一檔股票。跟自選股一樣用 (會員 id, 股票代號) 當複合主鍵：同一檔只記一筆，
 * 加碼、減碼時更新股數和平均成本。
 */
@Entity
@Table(name = "stock_holding")
public class StockHolding {

    @EmbeddedId
    private Key id;

    @MapsId("stockId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false)
    private Long shares;

    @Column(name = "average_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal averageCost;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StockHolding() {
    }

    public StockHolding(long memberId, Stock stock, long shares, BigDecimal averageCost, LocalDateTime updatedAt) {
        this.id = new Key(memberId, stock.getStockId());
        this.stock = stock;
        this.shares = shares;
        this.averageCost = averageCost;
        this.updatedAt = updatedAt;
    }

    public Key getId() { return id; }
    public long getMemberId() { return id.getMemberId(); }
    public Stock getStock() { return stock; }
    public long getShares() { return shares; }
    public BigDecimal getAverageCost() { return averageCost; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "member_id")
        private Long memberId;

        @Column(name = "stock_id", length = 10)
        private String stockId;

        protected Key() {
        }

        public Key(long memberId, String stockId) {
            this.memberId = memberId;
            this.stockId = stockId;
        }

        public Long getMemberId() { return memberId; }
        public String getStockId() { return stockId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(memberId, other.memberId)
                    && Objects.equals(stockId, other.stockId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberId, stockId);
        }
    }
}
