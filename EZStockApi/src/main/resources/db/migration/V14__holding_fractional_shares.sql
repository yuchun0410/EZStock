-- 美股可以買碎股（例如 0.12345 股），股數從整數改成最多小數點後 5 位。
-- 台股還是只能是整數，由程式檢查；既有的整數資料直接轉換，不會變。
ALTER TABLE stock_holding
    MODIFY COLUMN shares DECIMAL(19, 5) NOT NULL COMMENT '股數；台股是整數（1 張 = 1000 股），美股可以到小數點後 5 位';
