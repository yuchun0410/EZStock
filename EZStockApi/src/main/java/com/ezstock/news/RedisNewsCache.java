package com.ezstock.news;

import com.ezstock.datasource.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 用 Redis 存新聞，key：ezstock:news:{股票代號}:{日期}，value：新聞清單的 JSON。
 * Redis 出錯時當作沒有快取（直接去抓 FinMind），快取壞掉不應該讓新聞整個出不來。
 */
@Component
public class RedisNewsCache implements NewsCache {

    static final String KEY_PREFIX = "ezstock:news:";

    private static final Logger log = LoggerFactory.getLogger(RedisNewsCache.class);
    private static final TypeReference<List<NewsItem>> LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisNewsCache(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<List<NewsItem>> get(String stockId, LocalDate date) {
        try {
            String json = redis.opsForValue().get(key(stockId, date));
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, LIST_TYPE));
        } catch (RuntimeException e) {
            log.warn("[新聞] 讀取快取失敗 {} {}，改抓 FinMind：{}", stockId, date, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String stockId, LocalDate date, List<NewsItem> news, Duration ttl) {
        try {
            redis.opsForValue().set(key(stockId, date), jsonMapper.writeValueAsString(news), ttl);
        } catch (RuntimeException e) {
            log.warn("[新聞] 寫入快取失敗 {} {}：{}", stockId, date, e.getMessage());
        }
    }

    static String key(String stockId, LocalDate date) {
        return KEY_PREFIX + stockId + ":" + date;
    }
}
