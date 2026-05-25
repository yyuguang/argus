package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.config.AdminSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PasswordServiceImpl - 密码哈希服务")
class PasswordServiceImplTest {

    @Test
    @DisplayName("正确密码可以匹配 PBKDF2 哈希")
    void matchesValidPasswordHash() {
        PasswordServiceImpl passwordService = new PasswordServiceImpl(testProperties());
        String encodedHash = passwordService.hash("Argus@12345");

        assertTrue(passwordService.matches("Argus@12345", encodedHash));
        assertFalse(passwordService.matches("Wrong@12345", encodedHash));
    }

    @Test
    @DisplayName("畸形哈希返回校验失败而不是抛出异常")
    void malformedHashReturnsFalse() {
        PasswordServiceImpl passwordService = new PasswordServiceImpl(testProperties());

        assertFalse(passwordService.matches("Argus@12345", "PBKDF2$not-number$salt$hash"));
        assertFalse(passwordService.matches("Argus@12345", "PBKDF2$1000$invalid-base64$hash"));
        assertFalse(passwordService.matches("Argus@12345", "PBKDF2$0$c2FsdA==$aGFzaA=="));
    }

    private AdminSecurityProperties testProperties() {
        AdminSecurityProperties properties = new AdminSecurityProperties();
        properties.setPasswordIterations(1000);
        properties.setPasswordSaltBytes(8);
        properties.setPasswordHashBytes(16);
        return properties;
    }
}
