package com.lnzz.argus.codeindex.support;

import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @classname: ScmCodeIndexWorkspace
 * @author: Fantasy
 * @date: 2026/05/19 17:40
 * @description: SCM 源码索引临时工作区，保存按已知路径读取到本地的仓库文件快照。
 */
@Data
public class ScmCodeIndexWorkspace implements AutoCloseable {

    /**
     * 临时仓库根目录。
     */
    private final Path repositoryRoot;

    /**
     * 成功读取的文件路径。
     */
    private final List<String> loadedFilePaths = new ArrayList<>();

    /**
     * 读取失败的文件路径。
     */
    private final List<String> failedFilePaths = new ArrayList<>();

    /**
     * 读取或路径校验告警。
     */
    private final List<String> warnings = new ArrayList<>();

    @Override
    public void close() throws IOException {
        if (repositoryRoot == null || !Files.exists(repositoryRoot)) {
            return;
        }
        try (var stream = Files.walk(repositoryRoot)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
