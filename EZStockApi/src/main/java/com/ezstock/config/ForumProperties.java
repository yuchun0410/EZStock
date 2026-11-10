package com.ezstock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 對應 application.properties 的 ezstock.forum.*
 *
 * @param hotGravity 熱門排序的時間衰減：越大，舊文章掉得越快。
 *                   文章少的小論壇用 0.8，幾天前的好文章還留得住；
 *                   像 Hacker News 這種每小時上百篇的大網站用 1.5～1.8，熱門只看最近一兩天
 */
@ConfigurationProperties(prefix = "ezstock.forum")
public record ForumProperties(Double hotGravity) {

    public static final double DEFAULT_HOT_GRAVITY = 0.8;

    public ForumProperties {
        hotGravity = hotGravity == null || hotGravity <= 0 ? DEFAULT_HOT_GRAVITY : hotGravity;
    }
}
