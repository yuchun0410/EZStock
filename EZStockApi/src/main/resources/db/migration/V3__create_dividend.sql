CREATE TABLE dividend (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    stock_id      VARCHAR(10)   NOT NULL,
    ex_date       DATE          NOT NULL,
    cash_dividend DECIMAL(10,4) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dividend_stock_date (stock_id, ex_date),
    CONSTRAINT fk_dividend_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;