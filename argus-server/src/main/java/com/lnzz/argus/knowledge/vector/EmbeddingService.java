package com.lnzz.argus.knowledge.vector;

import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.review.entity.ReviewIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 文本向量化服务 — 将评审问题和错误模式转为向量，供 Redis Stack 语义检索。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "argus.vector.enabled", havingValue = "true")
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final int timeoutSec;
    private final ExecutorService embedExecutor;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            @Value("${argus.embedding.timeout-sec:30}") int timeoutSec) {
        this.embeddingModel = embeddingModel;
        this.timeoutSec = timeoutSec;
        this.embedExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "argus-embed");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 将评审问题转为向量。
     *
     * @return 向量数组，失败时返回空数组（不阻断主流程）
     */
    public float[] embedIssue(ReviewIssue issue) {
        if (issue == null) {
            return new float[0];
        }
        String text = buildIssueEmbeddingText(issue);
        return embed(text);
    }

    /**
     * 将错误事件转为向量。
     *
     * @return 向量数组，失败时返回空数组（不阻断主流程）
     */
    public float[] embedErrorPattern(ErrorEvent event) {
        if (event == null) {
            return new float[0];
        }
        String text = buildErrorEmbeddingText(event);
        return embed(text);
    }

    // ======================== 嵌入文本构建 ========================

    String buildIssueEmbeddingText(ReviewIssue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(issue.getSeverity()).append(']');
        sb.append('[').append(issue.getCategory()).append(']');
        if (issue.getRule() != null) sb.append(' ').append(issue.getRule());
        if (issue.getFilePath() != null) sb.append(' ').append(issue.getFilePath());
        if (issue.getDescription() != null) sb.append(' ').append(issue.getDescription());
        if (issue.getSuggestion() != null) sb.append(' ').append(issue.getSuggestion());
        return sb.toString();
    }

    String buildErrorEmbeddingText(ErrorEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getErrorType() != null) sb.append(event.getErrorType());
        if (event.getAppName() != null) sb.append(' ').append(event.getAppName());
        if (event.getClassName() != null) sb.append(' ').append(event.getClassName());
        if (event.getErrorMessage() != null) sb.append(' ').append(event.getErrorMessage());
        return sb.toString().trim();
    }

    // ======================== 内部 ========================

    private float[] embed(String text) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> embeddingModel.embed(text), embedExecutor)
                    .get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("向量化超时 ({}s)，降级返回空向量", timeoutSec);
            return new float[0];
        } catch (Exception e) {
            log.warn("向量化失败，降级返回空向量: {}", e.getMessage());
            return new float[0];
        }
    }
}
