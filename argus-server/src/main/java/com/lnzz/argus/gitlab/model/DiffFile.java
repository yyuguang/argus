package com.lnzz.argus.gitlab.model;

import lombok.Data;

import java.util.List;

/**
 * GitLab MR Diff 文件信息
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class DiffFile {

    /** 旧文件路径 */
    private String oldPath;

    /** 新文件路径 */
    private String newPath;

    /** 是否新增文件 */
    private boolean newFile;

    /** 是否删除文件 */
    private boolean deletedFile;

    /** 是否重命名 */
    private boolean renamedFile;

    /** diff 内容（unified diff 格式） */
    private String diff;

    /** 变更新增的行 */
    private List<DiffLine> addedLines;

    /** 变更删除的行 */
    private List<DiffLine> removedLines;

    /**
     * 是否为 Java 文件
     */
    public boolean isJavaFile() {
        return newPath != null && newPath.endsWith(".java");
    }

    /**
     * Diff 行信息
     */
    @Data
    public static class DiffLine {
        /** 行号 */
        private int lineNumber;
        /** 行内容 */
        private String content;

        public DiffLine(int lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }
}
