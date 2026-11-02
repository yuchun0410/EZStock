package com.ezstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 使用者自己輸入金額的帳戶：現金或負債。
 */
@Entity
@Table(name = "money_account")
public class MoneyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountKind kind;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal balance;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MoneyAccount() {
    }

    public MoneyAccount(long memberId, AccountKind kind, String name, BigDecimal balance, LocalDateTime updatedAt) {
        this.memberId = memberId;
        this.kind = kind;
        this.name = name;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    /** 種類建立後就不能改（現金不會變成負債），要改請刪掉重建 */
    public void update(String name, BigDecimal balance, LocalDateTime updatedAt) {
        this.name = name;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public AccountKind getKind() { return kind; }
    public String getName() { return name; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
