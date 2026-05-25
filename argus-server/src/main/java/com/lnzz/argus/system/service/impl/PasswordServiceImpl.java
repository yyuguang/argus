package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.config.AdminSecurityProperties;
import com.lnzz.argus.system.service.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Portal 用户密码服务。
 * <p>
 * 密码统一使用 PBKDF2WithHmacSHA256 加盐哈希。数据库不保存明文密码，
 * 登录校验时使用常量时间比较，降低时序侧信道风险。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordServiceImpl implements PasswordService {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "PBKDF2";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AdminSecurityProperties properties;

    /**
     * 对明文密码生成加盐哈希。
     *
     * @param rawPassword 明文密码
     * @return PBKDF2$iterations$salt$hash 格式的哈希串
     */
    @Override
    public String hash(String rawPassword) {
        byte[] salt = new byte[properties.getPasswordSaltBytes()];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, properties.getPasswordIterations());
        return PREFIX + "$"
                + properties.getPasswordIterations() + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验明文密码是否匹配数据库哈希。
     *
     * @param rawPassword 明文密码
     * @param encodedHash 数据库存储的哈希
     * @return true 表示匹配
     */
    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null || !encodedHash.startsWith(PREFIX + "$")) {
            return false;
        }
        String[] parts = encodedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            log.warn("密码哈希格式无效，已按校验失败处理: reason={}", ex.getMessage());
            return false;
        }
    }

    /**
     * 执行 PBKDF2 派生计算。
     * <p>该方法只处理算法计算，不记录明文密码，避免敏感信息进入日志。</p>
     */
    private byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    rawPassword.toCharArray(),
                    salt,
                    iterations,
                    properties.getPasswordHashBytes() * 8);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("密码哈希计算失败", ex);
        }
    }
}
