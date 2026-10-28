package com.ezstock.datasource.finmind;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 對應 application.properties 的 finmind.api.*
 */
@ConfigurationProperties(prefix = "finmind.api")
public record FinMindProperties(String url, String token) {

    /**
     * 有填真正的 token 才算有；沒填就用匿名額度。
     * FinMind 的 token 只會是英數字和符號，出現其他字元（例如中文佔位文字）一律當作沒填。
     * 注意：.properties 預設不是用 UTF-8 讀取，所以不能拿中文字串來比對。
     */
    public boolean hasToken() {
        return token != null
                && !token.isBlank()
                && !token.equals("YOUR_FINMIND_TOKEN")
                && token.chars().allMatch(c -> c > 32 && c < 127);
    }
}
