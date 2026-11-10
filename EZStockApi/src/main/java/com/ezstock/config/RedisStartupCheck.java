package com.ezstock.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 啟動完成後 ping 一次 Redis，在 log 裡就能看到 Redis 有沒有連上。
 * Redis 是懶連線（第一次用到才連），不檢查的話要等到有人登入才會發現沒開。
 * 這裡失敗只記 WARN、不讓程式啟動失敗：查股價、回測不需要 Redis，只有會員功能需要。
 */
@Component
public class RedisStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupCheck.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisStartupCheck(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        long started = System.currentTimeMillis();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String reply = connection.ping();
            log.info("[Redis] 連線成功（ping 回應 {}，{} ms）", reply, System.currentTimeMillis() - started);
        } catch (Exception e) {
            // 外層的訊息通常只有「Unable to connect to Redis」，看不出原因；
            // 往下找最根本的例外（例如密碼錯、沒開 TLS 被斷線、主機名稱查不到），一起印出來
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            log.warn("[Redis] 連線失敗，會員登入功能將無法使用：{}（根本原因：{}: {}）。"
                            + "本機請確認 brew services start redis；正式環境請檢查 REDIS_URL（密碼、rediss://）",
                    e.getMessage(), root.getClass().getSimpleName(), root.getMessage());
        }
    }
}
