package com.ezstock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 「現在幾點」也交給 Spring 管理。
 * 程式裡用 LocalDate.now(clock) 取得今天，測試時就能換成固定日期的 Clock。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Taipei"));
    }
}
