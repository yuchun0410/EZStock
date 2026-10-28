-- 股票分割、面額變更：股數會變多（或變少），價格跟著等比例調整，持有人的總價值不變。
-- 回測時遇到這一天要調整股數，不然會誤以為價格暴跌（例如 0050 在 2025-06-18 一拆四）。
CREATE TABLE stock_split (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    stock_id     VARCHAR(10)   NOT NULL,
    split_date   DATE          NOT NULL COMMENT '恢復交易、開始用新股數計價的第一天',
    split_type   VARCHAR(20)   NOT NULL COMMENT 'SPLIT 股票分割、PAR_VALUE_CHANGE 面額變更',
    before_price DECIMAL(12,4) NOT NULL COMMENT '事件前最後一個交易日的收盤價',
    after_price  DECIMAL(12,4) NOT NULL COMMENT '恢復交易的參考價',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_split_stock_date (stock_id, split_date),
    CONSTRAINT fk_stock_split_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
