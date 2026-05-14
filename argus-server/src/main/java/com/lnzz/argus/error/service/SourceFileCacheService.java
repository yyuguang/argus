package com.lnzz.argus.error.service;

import com.lnzz.argus.scm.entity.ScmConfig;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * SCM 单文件源码缓存服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface SourceFileCacheService {

    record CachedSourceFile(
            String content,
            LocalDateTime cachedAt,
            LocalDateTime lastHitAt,
            int size,
            String ref
    ) {}

    String getOrLoad(ScmConfig config, String ref, String filePath, Supplier<String> loader);

    void clear();
}
