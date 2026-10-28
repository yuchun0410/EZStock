package com.ezstock.datasource.finmind;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 對應 application.properties 的 finmind.api.*
 */
@ConfigurationProperties(prefix = "finmind.api")
public record FinMindProperties(String url, String token) {

    /** 有填真正的 token 才算有；沒填就用匿名額度 */
    public boolean hasToken() {
        return token != null
                && !token.isBlank()
                && !token.equals("YOUR_FINMIND_TOKEN")
                && !token.equals("之後填入");
    }
}
