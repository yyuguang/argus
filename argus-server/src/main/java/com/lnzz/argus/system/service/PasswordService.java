package com.lnzz.argus.system.service;

/**
 * @classname: PasswordService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: Portal 用户密码哈希与校验接口。
 */
public interface PasswordService {

    /**
     * 对明文密码生成加盐哈希。
     *
     * @param rawPassword 明文密码
     * @return PBKDF2$iterations$salt$hash 格式的哈希串
     */
    String hash(String rawPassword);

    /**
     * 校验明文密码是否匹配数据库哈希。
     *
     * @param rawPassword 明文密码
     * @param encodedHash 数据库存储的哈希
     * @return true 表示匹配
     */
    boolean matches(String rawPassword, String encodedHash);
}
