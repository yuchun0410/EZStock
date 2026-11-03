package com.ezstock.line;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 驗證 webhook 請求真的是 LINE 送來的。
 * webhook 網址是公開的，任何人都能 POST 假訊息進來；LINE 會用 channel secret 對整個 body 做 HMAC-SHA256，
 * 放在 X-Line-Signature header。我們用同一把 secret 再算一次，一樣才處理。
 * 比對用 MessageDigest.isEqual（固定時間比較），不用 equals，避免用回應時間一個字一個字猜出簽章。
 */
@Component
public class LineSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final LineProperties properties;

    public LineSignatureVerifier(LineProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(byte[] body, String signature) {
        if (!properties.enabled() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.channelSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] expected = mac.doFinal(body);
            byte[] actual = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("無法計算 LINE 簽章", e);
        }
    }
}
