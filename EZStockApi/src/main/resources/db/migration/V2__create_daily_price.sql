CREATE TABLE daily_price (
                             id          BIGINT        NOT NULL AUTO_INCREMENT,
                             stock_id    VARCHAR(10)   NOT NULL,
                             trade_date  DATE          NOT NULL,
                             open_price  DECIMAL(10,2) NOT NULL,
                             high_price  DECIMAL(10,2) NOT NULL,
                             low_price   DECIMAL(10,2) NOT NULL,
                             close_price DECIMAL(10,2) NOT NULL,
                             volume      BIGINT        NOT NULL,
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_daily_price_stock_date (stock_id, trade_date),
                             CONSTRAINT fk_daily_price_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;