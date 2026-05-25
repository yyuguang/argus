package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Portal 后台安全配置。
 * <p>集中配置 Token 有效期和密码哈希参数，避免安全参数散落在业务代码中。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.admin.security")
public class AdminSecurityProperties {

    /** Portal Token 有效期，单位小时。 */
    private long tokenExpireHours = 12;

    /** PBKDF2 迭代次数。 */
    private int passwordIterations = 120000;

    /** PBKDF2 盐长度，单位字节。 */
    private int passwordSaltBytes = 16;

    /** PBKDF2 输出长度，单位字节。 */
    private int passwordHashBytes = 32;
}
