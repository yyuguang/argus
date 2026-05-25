package com.lnzz.argus.codeindex.scanner;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: ModuleScanResult
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: Maven 模块扫描结果，表达模块路径、父子关系、构建类型和扫描统计。
 */
@Data
public class ModuleScanResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模块名称，优先取 artifactId。
     */
    private String moduleName;

    /**
     * 模块相对仓库路径，根模块为空字符串。
     */
    private String modulePath;

    /**
     * 父模块相对仓库路径，根模块为空。
     */
    private String parentModulePath;

    /**
     * 构建类型：MAVEN/DISCOVERED/UNKNOWN。
     */
    private String buildType;

    /**
     * Maven packaging，未声明时默认为 jar。
     */
    private String packaging;

    /**
     * 模块源码根列表。
     */
    private List<String> sourceRoots = new ArrayList<>();

    /**
     * 模块 Java 文件数量。
     */
    private Integer javaFileCount = 0;

    /**
     * 模块 Java 类型数量。
     */
    private Integer classCount = 0;

    /**
     * 模块扫描状态。
     */
    private String scanStatus;

    /**
     * 模块扫描告警。
     */
    private List<String> warnings = new ArrayList<>();
}
