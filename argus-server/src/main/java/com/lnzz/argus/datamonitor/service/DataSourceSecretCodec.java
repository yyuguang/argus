package com.lnzz.argus.datamonitor.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据源密码加密组件。
 *
 * <p>优先使用环境变量 ARGUS_DATASOURCE_SECRET_KEY；未配置时使用应用内默认密钥派生，
 * 避免密码明文落库。生产环境建议注入独立密钥。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class DataSourceSecretCodec {

    private static final String PREFIX = "AES_GCM:";
    private static final String KEY_ENV = "ARGUS_DATASOURCE_SECRET_KEY";
    private static final String DEFAULT_KEY_MATERIAL = "argus-data-monitor-default-secret";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("数据源密码加密失败", e);
        }
    }

    public String decrypt(String secret) {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        if (!secret.startsWith(PREFIX)) {
            return secret;
        }
        try {
            String payload = secret.substring(PREFIX.length());
            String[] parts = payload.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("数据源密码解密失败", e);
        }
    }

    private SecretKeySpec keySpec() throws Exception {
        String material = System.getenv(KEY_ENV);
        if (!StringUtils.hasText(material)) {
            material = DEFAULT_KEY_MATERIAL;
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
