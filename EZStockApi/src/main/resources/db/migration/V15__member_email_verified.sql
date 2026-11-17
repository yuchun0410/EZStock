-- Email 驗證：註冊後寄驗證信，點信裡的連結就記下驗證時間；NULL 代表還沒驗證（或根本沒有 Email）。
-- 這個功能上線前註冊的會員，先全部當成已驗證，避免大家突然被要求驗證。
ALTER TABLE member
    ADD COLUMN email_verified_at DATETIME(3) NULL COMMENT 'Email 驗證時間；NULL 代表還沒驗證或沒有 Email' AFTER email;

UPDATE member SET email_verified_at = created_at WHERE email IS NOT NULL;
