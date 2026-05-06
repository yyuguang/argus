package com.lnzz.argus.scm.model;

import lombok.Data;

import java.util.List;

/**
 * PR/MR Diff 文件信息
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class DiffFile {

    private static final java.util.Set<String> REVIEWABLE_EXTENSIONS = java.util.Set.of(
            ".java", ".sql", ".xml", ".yml", ".yaml", ".properties", ".json", ".sh", ".md", ".txt"
    );

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

    public boolean isJavaFile() {
        return newPath != null && newPath.endsWith(".java");
    }

    public boolean isSqlFile() {
        return hasExtension(".sql");
    }

    public boolean isConfigFile() {
        return hasExtension(".xml") || hasExtension(".yml") || hasExtension(".yaml")
                || hasExtension(".properties") || hasExtension(".json");
    }

    public boolean isReviewableFile() {
        String filePath = getEffectivePath();
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        if (filePath.endsWith("Dockerfile")) {
            return true;
        }
        return REVIEWABLE_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }

    public String getEffectivePath() {
        return newPath != null && !newPath.isBlank() ? newPath : oldPath;
    }

    public String getLanguageTag() {
        String filePath = getEffectivePath();
        if (filePath == null) {
            return "text";
        }
        if (filePath.endsWith(".java")) {
            return "java";
        }
        if (filePath.endsWith(".sql")) {
            return "sql";
        }
        if (filePath.endsWith(".xml")) {
            return "xml";
        }
        if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
            return "yaml";
        }
        if (filePath.endsWith(".properties")) {
            return "properties";
        }
        if (filePath.endsWith(".json")) {
            return "json";
        }
        if (filePath.endsWith(".sh")) {
            return "bash";
        }
        if (filePath.endsWith("Dockerfile")) {
            return "dockerfile";
        }
        if (filePath.endsWith(".md")) {
            return "markdown";
        }
        return "text";
    }

    private boolean hasExtension(String extension) {
        String filePath = getEffectivePath();
        return filePath != null && filePath.endsWith(extension);
    }

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
