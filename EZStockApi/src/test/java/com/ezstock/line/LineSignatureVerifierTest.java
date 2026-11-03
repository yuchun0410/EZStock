package com.ezstock.line;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class LineSignatureVerifierTest {

    private static final String SECRET = "test-channel-secret";
    private final LineSignatureVerifier verifier = new LineSignatureVerifier(new LineProperties(SECRET, "token"));

    @Test
    @DisplayName("用 channel secret 算出來的簽章：通過")
    void valid() throws Exception {
        byte[] body = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(body, sign(body, SECRET))).isTrue();
    }

    @Test
    @DisplayName("內容被改過、用別的 secret 簽、沒有簽章、亂碼：全部不通過")
    void invalid() throws Exception {
        byte[] body = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body, SECRET);

        assertThat(verifier.isValid("{\"events\":[1]}".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        assertThat(verifier.isValid(body, sign(body, "other-secret"))).isFalse();
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, "not-base64!!")).isFalse();
    }

    @Test
    @DisplayName("沒有設定 LINE：一律不通過")
    void disabled() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        LineSignatureVerifier off = new LineSignatureVerifier(new LineProperties(null, null));

        assertThat(off.isValid(body, sign(body, SECRET))).isFalse();
    }

    static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
