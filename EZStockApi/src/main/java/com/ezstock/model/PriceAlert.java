package com.ezstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 到價提醒 */
@Entity
@Table(name = "price_alert")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertDirection direction;

    @Column(name = "target_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal targetPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    protected PriceAlert() {
    }

    public PriceAlert(long memberId, Stock stock, AlertDirection direction, BigDecimal targetPrice,
                      LocalDateTime createdAt) {
        this.memberId = memberId;
        this.stock = stock;
        this.direction = direction;
        this.targetPrice = targetPrice;
        this.createdAt = createdAt;
    }

    public void markTriggered(LocalDateTime time) {
        this.triggeredAt = time;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Stock getStock() { return stock; }
    public AlertDirection getDirection() { return direction; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
}
