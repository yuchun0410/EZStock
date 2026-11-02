package com.ezstock.security;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * RS256 用的一對金鑰：私鑰簽名、公鑰驗證。
 * 只需要提供私鑰，公鑰從私鑰裡的 modulus 和 public exponent 算出來，兩把一定配得起來。
 *
 * @param keyId 金鑰編號（公鑰的 SHA-256 前 16 碼），會放進 JWT header 的 kid，換金鑰時可以分辨；log 也只印它
 */
public record RsaKeys(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey, String keyId) {

    private static final int KEY_SIZE = 2048;

    /**
     * 解析環境變數裡的私鑰。可以是整個 PEM（含 BEGIN/END），也可以是接成一行的 Base64。
     * 錯誤訊息不包含金鑰內容。
     */
    public static RsaKeys fromPrivateKey(String text) {
        String base64 = text
                .replaceAll("-----(BEGIN|END) [A-Z ]*PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (privateKey.getModulus().bitLength() < KEY_SIZE) {
                throw new IllegalStateException("JWT 私鑰長度不足 " + KEY_SIZE + " bits");
            }
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RsaKeys(publicKey, privateKey, keyIdOf(publicKey));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // 不把原始例外訊息帶出去，避免任何金鑰片段出現在 log
            throw new IllegalStateException("JWT 私鑰格式錯誤，需要 PKCS#8 格式的 RSA 私鑰（"
                    + e.getClass().getSimpleName() + "）");
        }
    }

    /** 沒有設定私鑰時，產生一把只存在記憶體的臨時金鑰 */
    public static RsaKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            var pair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
            return new RsaKeys(publicKey, (RSAPrivateCrtKey) pair.getPrivate(), keyIdOf(publicKey));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支援 RSA", e);
        }
    }

    private static String keyIdOf(RSAPublicKey publicKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public BigInteger modulus() {
        return publicKey.getModulus();
    }

    /** 不印私鑰 */
    @Override
    public String toString() {
        return "RsaKeys[keyId=" + keyId + ", bits=" + modulus().bitLength() + "]";
    }
}
