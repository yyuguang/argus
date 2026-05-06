package com.lnzz.argus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Argus 主启动类
 * <p>百眼巨人 — AI 代码质量与接口监控平台</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class ArgusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArgusApplication.class, args);
    }
}
