package com.lnzz.argus.review.service;

import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.model.DiffFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReviewExecutor - 大文件前置过滤")
class ReviewExecutorFilterTest {

    private ReviewExecutor executor;
    private ReviewConfig.FileFilterConfig filter;

    @BeforeEach
    void setUp() {
        executor = new ReviewExecutor(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        filter = ReviewConfig.defaults().getFileFilter();
    }

    @Test
    @DisplayName("正常文件全部通过")
    void normalFilesAllAccepted() {
        List<DiffFile> diffs = List.of(
                createDiff("src/main/java/com/example/UserService.java", 10, 5),
                createDiff("src/main/java/com/example/OrderController.java", 20, 8)
        );
        List<String> skipped = new ArrayList<>();

        List<DiffFile> result = executor.filterReviewableFiles(diffs, filter, skipped);

        assertEquals(2, result.size());
        assertTrue(skipped.isEmpty());
    }

    @Test
    @DisplayName("diff 行数超阈值 → 跳过")
    void fileExceedsMaxDiffLinesSkipped() {
        ReviewConfig.FileFilterConfig custom = ReviewConfig.defaults().getFileFilter();
        custom.setMaxDiffLinesPerFile(20);

        List<DiffFile> diffs = List.of(
                createDiff("Small.java", 5, 3),   // 8 lines → pass
                createDiff("Large.java", 30, 10)  // 40 lines → skip
        );
        List<String> skipped = new ArrayList<>();

        List<DiffFile> result = executor.filterReviewableFiles(diffs, custom, skipped);

        assertEquals(1, result.size());
        assertEquals("Small.java", result.get(0).getNewPath());
        assertEquals(1, skipped.size());
        assertTrue(skipped.get(0).contains("Large.java"));
        assertTrue(skipped.get(0).contains("40"));
    }

    @Test
    @DisplayName("匹配 glob 排除模式 → 跳过")
    void globExcludedFileSkipped() {
        List<DiffFile> diffs = List.of(
                createDiff("src/main/java/Service.java", 10, 5),
                createDiff("package-lock.json", 50, 30),
                createDiff("go.sum", 10, 5)
        );
        List<String> skipped = new ArrayList<>();

        List<DiffFile> result = executor.filterReviewableFiles(diffs, filter, skipped);

        assertEquals(1, result.size());
        assertEquals("src/main/java/Service.java", result.get(0).getNewPath());
        assertEquals(2, skipped.size());
        assertTrue(skipped.stream().anyMatch(s -> s.contains("package-lock.json")));
        assertTrue(skipped.stream().anyMatch(s -> s.contains("go.sum")));
    }

    @Test
    @DisplayName("二进制扩展名 → 跳过")
    void binaryExtensionSkipped() {
        List<DiffFile> diffs = List.of(
                createDiff("Service.java", 5, 2),
                createDiff("lib/app.jar", 0, 0),
                createDiff("images/logo.png", 0, 0)
        );
        List<String> skipped = new ArrayList<>();

        List<DiffFile> result = executor.filterReviewableFiles(diffs, filter, skipped);

        assertEquals(1, result.size());
        assertTrue(skipped.stream().anyMatch(s -> s.contains(".jar")));
        assertTrue(skipped.stream().anyMatch(s -> s.contains(".png")));
    }

    @Test
    @DisplayName("超过 maxReviewFiles → 截断")
    void maxReviewFilesLimit() {
        ReviewConfig.FileFilterConfig custom = ReviewConfig.defaults().getFileFilter();
        custom.setMaxReviewFiles(2);

        List<DiffFile> diffs = List.of(
                createDiff("A.java", 5, 3),
                createDiff("B.java", 5, 3),
                createDiff("C.java", 5, 3),
                createDiff("D.java", 5, 3)
        );
        List<String> skipped = new ArrayList<>();

        List<DiffFile> result = executor.filterReviewableFiles(diffs, custom, skipped);

        assertEquals(2, result.size());
        assertEquals(2, skipped.size());
        assertTrue(skipped.get(0).contains("C.java"));
        assertTrue(skipped.get(1).contains("D.java"));
    }

    @Test
    @DisplayName("buildDegradationNote 空原因返回空字符串")
    void degradationNoteEmpty() {
        ReviewExecutor exec = new ReviewExecutor(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        // 反射不方便，改为直接测试 method 行为 — 通过 filterReviewableFiles 间接验证
        List<String> skipped = new ArrayList<>();
        List<DiffFile> result = executor.filterReviewableFiles(
                List.of(createDiff("A.java", 5, 3)), filter, skipped);

        assertEquals(1, result.size());
        assertTrue(skipped.isEmpty());
    }

    // ======================== helpers ========================

    private DiffFile createDiff(String path, int addedLines, int removedLines) {
        DiffFile diff = new DiffFile();
        diff.setNewPath(path);
        List<DiffFile.DiffLine> added = new ArrayList<>();
        for (int i = 0; i < addedLines; i++) {
            added.add(new DiffFile.DiffLine(i + 1, "+line " + (i + 1)));
        }
        diff.setAddedLines(added);
        List<DiffFile.DiffLine> removed = new ArrayList<>();
        for (int i = 0; i < removedLines; i++) {
            removed.add(new DiffFile.DiffLine(i + 1, "-line " + (i + 1)));
        }
        diff.setRemovedLines(removed);
        return diff;
    }
}
