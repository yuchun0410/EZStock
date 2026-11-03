-- 會員綁定的 LINE 帳號（LINE 使用者 id），用來推播每日淨資產回報。一個 LINE 帳號只能綁一個會員。
ALTER TABLE member
    ADD COLUMN line_user_id VARCHAR(64) NULL COMMENT 'LINE 使用者 id；沒綁定時為 NULL' AFTER google_sub,
    ADD UNIQUE KEY uk_member_line_user_id (line_user_id);
