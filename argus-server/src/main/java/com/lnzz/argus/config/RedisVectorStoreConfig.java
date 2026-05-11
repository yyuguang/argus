package com.lnzz.argus.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import static org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

/**
 * Redis Stack 向量存储配置
 *
 * @author lnzz
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "argus.vector.enabled", havingValue = "true")
public class RedisVectorStoreConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Jedis 客户端（专用于 RedisVectorStore，与 Spring Data Redis Lettuce 并存）
     */
    @Bean
    public JedisPooled jedisPooled() {
        if (redisPassword != null && !redisPassword.isEmpty()) {
            return new JedisPooled(redisHost, redisPort, "default", redisPassword);
        }
        return new JedisPooled(redisHost, redisPort);
    }

    /**
     * 代码评审 Issue 向量存储（Phase 1）
     */
    @Bean
    public RedisVectorStore reviewIssueVectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("idx:review:issue")
                .prefix("review:issue:")
                .contentFieldName("description")
                .embeddingFieldName("embedding")
                .vectorAlgorithm(RedisVectorStore.Algorithm.FLAT)
                .metadataFields(java.util.List.of(
                        MetadataField.tag("severity"),
                        MetadataField.tag("category"),
                        MetadataField.tag("author_id"),
                        MetadataField.tag("rule"),
                        MetadataField.tag("project_name")
                ))
                .initializeSchema(true)
                .build();
    }

    /**
     * 错误知识条目向量存储（Phase 2）
     */
    @Bean
    public RedisVectorStore knowledgeEntryVectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("idx:knowledge:entry")
                .prefix("knowledge:entry:")
                .contentFieldName("error_pattern")
                .embeddingFieldName("embedding")
                .vectorAlgorithm(RedisVectorStore.Algorithm.FLAT)
                .metadataFields(java.util.List.of(
                        MetadataField.tag("error_type"),
                        MetadataField.tag("app_name"),
                        MetadataField.tag("status")
                ))
                .initializeSchema(true)
                .build();
    }
}
