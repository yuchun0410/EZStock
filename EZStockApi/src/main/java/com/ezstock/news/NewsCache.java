package com.ezstock.news;

import com.ezstock.datasource.NewsItem;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 新聞快取：同一檔股票同一天的新聞，抓過一次就先存起來，不要每次開首頁都打 FinMind。
 * 抽成介面，測試時可以換成記憶體版本，不用連 Redis。
 */
public interface NewsCache {

    /** 沒有快取回 empty；有快取但當天沒新聞回空的 List（也是一種結果，不用再抓） */
    Optional<List<NewsItem>> get(String stockId, LocalDate date);

    void put(String stockId, LocalDate date, List<NewsItem> news, Duration ttl);
}
