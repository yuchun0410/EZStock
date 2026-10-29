-- 會員的自選股。一個會員、一檔股票只會有一筆，所以用 (member_id, stock_id) 當複合主鍵，不另外開 id。
-- 會員刪除時自選股跟著刪（ON DELETE CASCADE）；股票是共用資料，不會因為自選股被刪。
CREATE TABLE watchlist_item (
    member_id BIGINT      NOT NULL,
    stock_id  VARCHAR(10) NOT NULL,
    added_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (member_id, stock_id),
    CONSTRAINT fk_watchlist_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
