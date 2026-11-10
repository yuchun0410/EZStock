-- 用 LINE 登入的會員不一定有 Email（要另外跟 LINE 申請權限、使用者也要同意），所以 Email 改成可以是 NULL。
-- MySQL 的 UNIQUE 允許多筆 NULL，有 Email 的會員仍然不能重複。
ALTER TABLE member
    MODIFY COLUMN email VARCHAR(254) NULL COMMENT '一律存小寫，登入時不分大小寫；只用 LINE 登入且沒提供 Email 時為 NULL';
