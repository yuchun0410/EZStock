package com.ezstock.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 開啟 @Scheduled 排程（每日更新股價） */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
