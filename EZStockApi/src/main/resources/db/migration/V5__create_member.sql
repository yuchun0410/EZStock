-- 會員。可以用 Email + 密碼註冊，也可以用 Google 登入，兩種可以綁在同一個帳號。
-- 密碼只存 BCrypt 雜湊，不存明碼；只用 Google 登入的會員沒有密碼（password_hash 是 NULL）。
CREATE TABLE member (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NOT NULL COMMENT '一律存小寫，登入時不分大小寫',
    password_hash VARCHAR(100) NULL     COMMENT 'BCrypt 雜湊；只用 Google 登入時為 NULL',
    google_sub    VARCHAR(255) NULL     COMMENT 'Google 帳號的唯一編號（ID Token 的 sub）；沒綁 Google 時為 NULL',
    display_name  VARCHAR(50)  NOT NULL,
    avatar_url    VARCHAR(500) NULL,
    created_at    DATETIME(3)  NOT NULL,
    last_login_at DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_google_sub (google_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
