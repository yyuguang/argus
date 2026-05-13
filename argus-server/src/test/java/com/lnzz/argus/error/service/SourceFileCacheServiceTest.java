package com.lnzz.argus.error.service;

import com.lnzz.argus.config.ErrorProcessingProperties;
import com.lnzz.argus.error.service.impl.SourceFileCacheServiceImpl;
import com.lnzz.argus.scm.entity.ScmConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SourceFileCacheService - SCM 源码缓存")
class SourceFileCacheServiceTest {

    private ErrorProcessingProperties properties;
    private SourceFileCacheService service;
    private ScmConfig config;

    @BeforeEach
    void setUp() {
        properties = new ErrorProcessingProperties();
        service = new SourceFileCacheServiceImpl(properties, new SimpleMeterRegistry());
        config = new ScmConfig();
        config.setScmProvider("github");
        config.setProjectId(100L);
        config.setRepoOwner("lnzz");
        config.setRepoName("argus");
    }

    @Test
    @DisplayName("同一 ref + filePath 在 TTL 内只加载一次")
    void cacheHitLoadsOnceWithinTtl() {
        AtomicInteger loadCount = new AtomicInteger();

        String first = service.getOrLoad(config, "main", "src/App.java",
                () -> "content-" + loadCount.incrementAndGet());
        String second = service.getOrLoad(config, "main", "src/App.java",
                () -> "content-" + loadCount.incrementAndGet());

        assertEquals("content-1", first);
        assertEquals("content-1", second);
        assertEquals(1, loadCount.get());
    }

    @Test
    @DisplayName("TTL 为 0 时每次都重新加载")
    void expiredCacheReloads() {
        properties.getSource().setCacheTtlSeconds(0);
        AtomicInteger loadCount = new AtomicInteger();

        service.getOrLoad(config, "main", "src/App.java", () -> "content-" + loadCount.incrementAndGet());
        String second = service.getOrLoad(config, "main", "src/App.java",
                () -> "content-" + loadCount.incrementAndGet());

        assertEquals("content-2", second);
        assertEquals(2, loadCount.get());
    }

    @Test
    @DisplayName("源码内容按单文件字符数截断")
    void contentIsTruncatedByMaxFileChars() {
        properties.getSource().setMaxFileChars(4);

        String content = service.getOrLoad(config, "main", "src/App.java", () -> "abcdef");

        assertEquals("abcd", content);
    }

    @Test
    @DisplayName("null 结果也进入短期缓存，避免文件不存在时重复打 SCM")
    void nullResultIsCached() {
        AtomicInteger loadCount = new AtomicInteger();

        String first = service.getOrLoad(config, "main", "missing.java", () -> {
            loadCount.incrementAndGet();
            return null;
        });
        String second = service.getOrLoad(config, "main", "missing.java", () -> {
            loadCount.incrementAndGet();
            return null;
        });

        assertEquals(null, first);
        assertEquals(null, second);
        assertEquals(1, loadCount.get());
    }
}
