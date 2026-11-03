package com.ezstock.line;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(LineProperties.class)
public class LineConfig {

    private static final Logger log = LoggerFactory.getLogger(LineConfig.class);

    /** 呼叫 LINE API 用的 RestClient，token 放在預設的 Authorization header，之後每個請求自動帶上 */
    @Bean
    public RestClient lineRestClient(LineProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        if (properties.enabled()) {
            log.info("[LINE] 已設定 Messaging API");
        } else {
            log.warn("[LINE] 沒有設定 EZSTOCK_LINE_CHANNEL_SECRET / EZSTOCK_LINE_CHANNEL_TOKEN，LINE Bot 不會運作");
        }
        return RestClient.builder()
                .baseUrl("https://api.line.me")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (properties.enabled() ? properties.channelToken() : ""))
                .build();
    }
}
