package com.ezstock.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 會員的一檔自選股。主鍵是 (會員 id, 股票代號) 的複合主鍵，用 @EmbeddedId 表示；
 * stock 用 @MapsId 對到主鍵裡的 stockId，所以 stock_id 這個欄位同時是主鍵的一部分、也是指向 stock 的外鍵。
 */
@Entity
@Table(name = "watchlist_item")
public class WatchlistItem {

    @EmbeddedId
    private Key id;

    @MapsId("stockId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    protected WatchlistItem() {
    }

    public WatchlistItem(long memberId, Stock stock, LocalDateTime addedAt) {
        this.id = new Key(memberId, stock.getStockId());
        this.stock = stock;
        this.addedAt = addedAt;
    }

    public Key getId() { return id; }
    public long getMemberId() { return id.getMemberId(); }
    public Stock getStock() { return stock; }
    public LocalDateTime getAddedAt() { return addedAt; }

    /** 複合主鍵。JPA 規定要實作 Serializable、equals、hashCode，還要有無參數建構子 */
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
