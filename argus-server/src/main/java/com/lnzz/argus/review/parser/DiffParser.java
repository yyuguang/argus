package com.lnzz.argus.review.parser;

import com.lnzz.argus.gitlab.model.DiffFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M2-A: Diff 解析器
 * <p>将 Git Unified Diff 格式解析为结构化数据</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class DiffParser {

    /** hunk 头部匹配: @@ -oldStart,oldCount +newStart,newCount @@ */
    private static final Pattern HUNK_PATTERN = Pattern.compile("@@ -(\\d+),?\\d* \\+(\\d+),?\\d* @@");

    /**
     * M2-A01~A03: 解析 Diff，提取新增行和删除行
     *
     * @param diffFile 变更文件
     */
    public void parseDiff(DiffFile diffFile) {
        if (diffFile.getDiff() == null || diffFile.getDiff().isBlank()) {
            diffFile.setAddedLines(new ArrayList<>());
            diffFile.setRemovedLines(new ArrayList<>());
            return;
        }

        List<DiffFile.DiffLine> addedLines = new ArrayList<>();
        List<DiffFile.DiffLine> removedLines = new ArrayList<>();

        String[] lines = diffFile.getDiff().split("\n");
        int newLineNum = 0;
        int oldLineNum = 0;

        for (String line : lines) {
            Matcher hunkMatcher = HUNK_PATTERN.matcher(line);
            if (hunkMatcher.find()) {
                oldLineNum = Integer.parseInt(hunkMatcher.group(1));
                newLineNum = Integer.parseInt(hunkMatcher.group(2));
                continue;
            }

            if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines.add(new DiffFile.DiffLine(newLineNum, line.substring(1)));
                newLineNum++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines.add(new DiffFile.DiffLine(oldLineNum, line.substring(1)));
                oldLineNum++;
            } else if (!line.startsWith("\\")) {
                newLineNum++;
                oldLineNum++;
            }
        }

        diffFile.setAddedLines(addedLines);
        diffFile.setRemovedLines(removedLines);
    }

    /**
     * M2-A06: 计算变更统计
     */
    public DiffStats calculateStats(List<DiffFile> diffs) {
        int totalAdded = 0;
        int totalRemoved = 0;
        for (DiffFile diff : diffs) {
            if (diff.getAddedLines() != null) {
                totalAdded += diff.getAddedLines().size();
            }
            if (diff.getRemovedLines() != null) {
                totalRemoved += diff.getRemovedLines().size();
            }
        }
        return new DiffStats(diffs.size(), totalAdded, totalRemoved);
    }

    /**
     * 变更统计
     */
    public record DiffStats(int fileCount, int addedLines, int removedLines) {
    }
}
