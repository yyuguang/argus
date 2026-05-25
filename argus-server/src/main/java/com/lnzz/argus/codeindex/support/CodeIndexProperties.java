package com.lnzz.argus.codeindex.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @classname: CodeIndexProperties
 * @author: Fantasy
 * @date: 2026/05/25 08:40
 * @description: 源码索引配置属性，控制异步扫描和 JavaParser 并行解析灰度开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.code-index")
public class CodeIndexProperties {

    /**
     * 是否启用管理端异步扫描任务。
     */
    private boolean asyncScanEnabled = true;

    /**
     * JavaParser 解析配置。
     */
    private Parser parser = new Parser();

    @Data
    public static class Parser {

        /**
         * 是否启用并行解析，首版默认关闭便于灰度。
         */
        private boolean parallelEnabled = false;

        /**
         * JavaParser 并行度。
         */
        private int parallelism = 2;

        /**
         * 并行解析任务队列大小。
         */
        private int queueSize = 1000;

        /**
         * Java 解析阶段进度更新间隔。
         */
        private int progressInterval = CodeIndexConstants.ScanTask.DEFAULT_PROGRESS_INTERVAL;
    }
}
