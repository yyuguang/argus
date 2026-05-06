package com.lnzz.argus.review.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.review.ai.CodingStandardsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 编码规范管理 API
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/standards")
@RequiredArgsConstructor
public class StandardsController {

    private final CodingStandardsLoader codingStandardsLoader;

    /**
     * 查看已加载的规范文件列表
     */
    @GetMapping("/files")
    public Result<Map<String, Object>> listLoadedFiles() {
        List<String> files = codingStandardsLoader.getLoadedFiles();
        return Result.success(Map.of(
                "files", files,
                "count", files.size()
        ));
    }

    /**
     * 刷新规范缓存（新增/修改规范文件后调用）
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshStandards() {
        List<String> files = codingStandardsLoader.refreshCache();
        log.info("规范缓存已刷新: {} 个文件", files.size());
        return Result.success("规范缓存已刷新", Map.of(
                "files", files,
                "count", files.size()
        ));
    }

    /**
     * 预览当前加载的规范内容（调试用）
     */
    @GetMapping("/preview")
    public Result<Map<String, Object>> previewStandards() {
        String content = codingStandardsLoader.loadCodingStandards();
        return Result.success(Map.of(
                "content", content,
                "length", content.length(),
                "estimatedTokens", content.length() / 4
        ));
    }
}
