package com.ezstock.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeysTest {

    private final RsaKeys generated = RsaKeys.generate();
    private final String base64 = Base64.getEncoder().encodeToString(generated.privateKey().getEncoded());

    @Test
    @DisplayName("一行 Base64 的私鑰：可以解析，公鑰從私鑰算出來")
    void base64() {
        RsaKeys keys = RsaKeys.fromPrivateKey(base64);

        assertThat(keys.publicKey()).isEqualTo(generated.publicKey());
        assertThat(keys.keyId()).isEqualTo(generated.keyId()).hasSize(16);
    }

    @Test
    @DisplayName("完整 PEM（含 BEGIN/END 與換行）也可以解析")
    void pem() {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + base64.replaceAll("(.{64})", "$1\n")
                + "\n-----END PRIVATE KEY-----\n";

        assertThat(RsaKeys.fromPrivateKey(pem).publicKey()).isEqualTo(generated.publicKey());
    }

    @Test
    @DisplayName("格式錯誤：錯誤訊息不包含金鑰內容")
    void invalid() {
        String broken = base64.substring(0, 100);

        assertThatThrownBy(() -> RsaKeys.fromPrivateKey(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 私鑰格式錯誤")
                .hasMessageNotContaining(broken.substring(0, 20));
    }

    @Test
    @DisplayName("toString 不印私鑰")
    void toStringHidesKey() {
        assertThat(generated.toString()).startsWith("RsaKeys[keyId=").doesNotContain(base64.substring(0, 20));
        assertThat(new JwtProperties(base64, null, null, null, false).toString())
                .contains("已設定").doesNotContain(base64.substring(0, 20));
    }
}
