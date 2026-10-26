CREATE TABLE stock (
                       stock_id VARCHAR(10) NOT NULL,
                       name     VARCHAR(50) NOT NULL,
                       type     VARCHAR(10) NOT NULL,
                       industry VARCHAR(50),
                       PRIMARY KEY (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;