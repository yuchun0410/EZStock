package com.ezstock.email;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 寄信服務共用的 HTTP 設定。
 *
 * 用 Java 內建的 HttpClient，不用 SimpleClientHttpRequestFactory（HttpURLConnection），原因：
 * - Apps Script 收到 POST 會回 302 轉址，HttpURLConnection 在 Spring 裡只對 GET 跟著轉址
 * - HttpURLConnection 收到 401 時拿不到回應內容，log 看不到服務商回的錯誤原因
 */
final class EmailHttp {

    private EmailHttp() {
    }

    /** 連線 5 秒、讀取 10 秒逾時：寄信服務沒回應時，不要讓註冊請求一直卡著 */
    static ClientHttpRequestFactory requestFactory() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
