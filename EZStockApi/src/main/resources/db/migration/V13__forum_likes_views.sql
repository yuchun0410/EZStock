-- 論壇：按讚、瀏覽數。
-- 讚數、瀏覽數跟留言數一樣直接存在文章上，列表頁不用每篇都 COUNT 一次；加減一律用原子更新。
ALTER TABLE post
    ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT '讚數' AFTER comment_count,
    ADD COLUMN view_count INT NOT NULL DEFAULT 0 COMMENT '瀏覽數（同一個人 30 分鐘內只算一次）' AFTER like_count,
    -- 熱門文章：只從最近一段時間的文章裡挑，用發文時間篩選
    ADD KEY idx_post_recent (created_at);

-- 誰按了哪篇的讚。主鍵 (post_id, member_id) 讓資料庫保證同一個人對同一篇只能按一次
CREATE TABLE post_like (
    post_id    BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (post_id, member_id),
    -- 「我按過讚的文章」：用會員找
    KEY idx_post_like_member (member_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
