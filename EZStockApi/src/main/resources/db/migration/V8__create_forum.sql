-- 論壇：文章、文章標記的股票、留言。
-- 刪除用「軟刪除」：只填 deleted_at，不真的刪掉資料，畫面顯示「已刪除」，留言串才不會斷掉。

CREATE TABLE post (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    member_id     BIGINT         NOT NULL COMMENT '作者',
    title         VARCHAR(100)   NOT NULL,
    content       VARCHAR(10000) NOT NULL COMMENT '純文字，顯示時由前端跳脫，避免 XSS',
    comment_count INT            NOT NULL DEFAULT 0 COMMENT '留言數，列表頁不用每篇都 COUNT 一次',
    created_at    DATETIME(3)    NOT NULL,
    updated_at    DATETIME(3)    NOT NULL,
    deleted_at    DATETIME(3)    NULL     COMMENT 'NULL 代表沒有被刪除',
    PRIMARY KEY (id),
    -- 列表頁：沒被刪除的文章，依時間由新到舊
    KEY idx_post_list (deleted_at, created_at, id),
    KEY idx_post_member (member_id),
    CONSTRAINT fk_post_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_stock (
    post_id  BIGINT      NOT NULL,
    stock_id VARCHAR(10) NOT NULL,
    PRIMARY KEY (post_id, stock_id),
    -- 個股頁「討論這檔的文章」：用股票代號找文章
    KEY idx_post_stock_stock (stock_id, post_id),
    CONSTRAINT fk_post_stock_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_stock_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL,
    member_id  BIGINT        NOT NULL COMMENT '作者',
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME(3)   NOT NULL,
    deleted_at DATETIME(3)   NULL,
    PRIMARY KEY (id),
    KEY idx_comment_post (post_id, id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
