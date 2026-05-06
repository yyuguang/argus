package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评审配置属性
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.review")
public class ReviewProperties {

    /** 评分阈值，低于此分数阻止合并 */
    private int blockThreshold = 60;

    /** 评审维度权重 */
    private Dimensions dimensions = new Dimensions();

    /** 扣分规则 */
    private Scoring scoring = new Scoring();

    @Data
    public static class Dimensions {
        private int compliance = 30;
        private int correctness = 25;
        private int dataIntegrity = 20;
        private int performance = 15;
        private int maintainability = 10;
    }

    @Data
    public static class Scoring {
        private int criticalDeduction = 15;
        private int majorDeduction = 8;
        private int minorDeduction = 3;
    }
}
