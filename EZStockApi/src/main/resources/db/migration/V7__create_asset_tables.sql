-- 資產儀表板。現金帳戶和負債欄位一樣（名稱 + 金額），放同一張表，用 kind 區分；
-- 股票持股多了股數、平均成本，而且要接股價，另外一張表。
-- 會員刪除時，資產資料跟著刪（ON DELETE CASCADE）。

CREATE TABLE money_account (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id  BIGINT        NOT NULL,
    kind       VARCHAR(20)   NOT NULL COMMENT 'CASH 現金、LIABILITY 負債',
    name       VARCHAR(50)   NOT NULL COMMENT '例如：台新活存、信貸、房貸',
    balance    DECIMAL(16,2) NOT NULL COMMENT '金額一律存正數；負債算淨資產時才扣掉',
    updated_at DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_money_account_member (member_id),
    CONSTRAINT fk_money_account_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stock_holding (
    member_id    BIGINT        NOT NULL,
    stock_id     VARCHAR(10)   NOT NULL,
    shares       BIGINT        NOT NULL COMMENT '股數（1 張 = 1000 股，零股也可以）',
    average_cost DECIMAL(12,4) NOT NULL COMMENT '每股平均成本（含手續費）',
    updated_at   DATETIME(3)   NOT NULL,
    PRIMARY KEY (member_id, stock_id),
    CONSTRAINT fk_holding_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_holding_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
