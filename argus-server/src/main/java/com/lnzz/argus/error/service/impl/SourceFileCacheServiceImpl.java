package com.lnzz.argus.error.service.impl;

import com.lnzz.argus.config.ErrorProcessingProperties;
import com.lnzz.argus.error.service.SourceFileCacheService;
import com.lnzz.argus.scm.entity.ScmConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 基于本地内存的 SCM 单文件源码缓存。
 * <p>只缓存 Prompt 所需的短期源码文本，不写入 MySQL、知识库或通知记录。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SourceFileCacheServiceImpl implements SourceFileCacheService {

    private final ErrorProcessingProperties properties;
    private final MeterRegistry meterRegistry;
    private final Map<String, CachedSourceFile> cache = new ConcurrentHashMap<>();

    @Override
    public String getOrLoad(ScmConfig config, String ref, String filePath, Supplier<String> loader) {
        String key = buildKey(config, ref, filePath);
        CachedSourceFile cached = cache.get(key);
        if (cached != null && !isExpired(cached)) {
            cache.put(key, new CachedSourceFile(cached.content(), cached.cachedAt(),
                    LocalDateTime.now(), cached.size(), cached.ref()));
            recordCacheHit(config);
            return cached.content();
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String content = loader.get();
            String limited = limitFileContent(content);
            LocalDateTime now = LocalDateTime.now();
            cache.put(key, new CachedSourceFile(limited, now, now,
                    limited != null ? limited.length() : 0, ref));
            recordScmFetch(config, "success");
            return limited;
        } catch (RuntimeException e) {
            recordScmFetch(config, "failure");
            throw e;
        } finally {
            sample.stop(Timer.builder("argus_scm_source_fetch_duration")
                    .description("SCM 单文件源码拉取耗时")
                    .tag("provider", provider(config))
                    .register(meterRegistry));
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private String buildKey(ScmConfig config, String ref, String filePath) {
        String repo = config.getProjectId() != null
                ? String.valueOf(config.getProjectId())
                : nullToEmpty(config.getRepoOwner()) + "/" + nullToEmpty(config.getRepoName());
        return provider(config) + "|" + repo + "|" + nullToEmpty(ref) + "|" + nullToEmpty(filePath);
    }

    private boolean isExpired(CachedSourceFile entry) {
        long ttlSeconds = properties.getSource().getCacheTtlSeconds();
        return ttlSeconds <= 0
                || Duration.between(entry.cachedAt(), LocalDateTime.now()).getSeconds() >= ttlSeconds;
    }

    private String limitFileContent(String content) {
        if (content == null) {
            return null;
        }
        int maxChars = properties.getSource().getMaxFileChars();
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars);
    }

    private void recordCacheHit(ScmConfig config) {
        Counter.builder("argus_scm_source_cache_hits_total")
                .description("SCM 源码缓存命中次数")
                .tag("provider", provider(config))
                .register(meterRegistry)
                .increment();
    }

    private void recordScmFetch(ScmConfig config, String status) {
        Counter.builder("argus_scm_source_fetch_total")
                .description("SCM 源码文件拉取次数")
                .tag("provider", provider(config))
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    private String provider(ScmConfig config) {
        return config != null && config.getScmProvider() != null ? config.getScmProvider() : "UNKNOWN";
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

}
