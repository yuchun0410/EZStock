-- 到價提醒：收盤價突破（ABOVE）或跌破（BELOW）目標價時，用 LINE 通知一次。
-- 通知過的不刪除，只記下 triggered_at，留著當紀錄；之後只看 triggered_at 是 NULL 的。
CREATE TABLE price_alert (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    member_id    BIGINT        NOT NULL,
    stock_id     VARCHAR(10)   NOT NULL,
    direction    VARCHAR(10)   NOT NULL COMMENT 'ABOVE 收盤 >= 目標價、BELOW 收盤 <= 目標價',
    target_price DECIMAL(12,4) NOT NULL,
    created_at   DATETIME(3)   NOT NULL,
    triggered_at DATETIME(3)   NULL     COMMENT '通知的時間；NULL 代表還在等',
    PRIMARY KEY (id),
    KEY idx_price_alert_member (member_id, triggered_at),
    CONSTRAINT fk_price_alert_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_price_alert_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
