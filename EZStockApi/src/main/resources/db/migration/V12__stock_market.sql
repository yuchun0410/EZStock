-- 支援美股：股票加上市場別（TW 台股、US 美股），既有資料都是台股。
-- 美股名稱、產業別比較長（例如 "Taiwan Semiconductor Manufacturing Company Ltd."），欄位放寬到 100 字。
ALTER TABLE stock
    ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'TW' COMMENT '市場：TW 台股、US 美股' AFTER type,
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN industry VARCHAR(100) NULL,
    ADD KEY idx_stock_market (market);
