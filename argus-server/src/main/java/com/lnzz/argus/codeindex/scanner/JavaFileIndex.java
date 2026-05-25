package com.lnzz.argus.codeindex.scanner;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: JavaFileIndex
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: Java 文件解析结果，表达源码文件中的类型、包名、imports 和源码行号。
 */
@Data
public class JavaFileIndex implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模块路径。
     */
    private String modulePath;

    /**
     * 源码根路径。
     */
    private String sourceRoot;

    /**
     * 文件相对仓库路径。
     */
    private String filePath;

    /**
     * 文件内容 SHA-256。
     */
    private String fileSha;

    /**
     * 包名，无 package 时为空字符串。
     */
    private String packageName;

    /**
     * 简单类名。
     */
    private String className;

    /**
     * 全限定类名。
     */
    private String qualifiedName;

    /**
     * Java 类型：CLASS/INTERFACE/ENUM/ANNOTATION/RECORD。
     */
    private String classKind;

    /**
     * 是否文件主类型。
     */
    private Boolean primaryType;

    /**
     * 类型起始行号。
     */
    private Integer lineStart;

    /**
     * 类型结束行号。
     */
    private Integer lineEnd;

    /**
     * imports 列表。
     */
    private List<String> imports = new ArrayList<>();

    /**
     * 解析状态：SUCCESS/FAILED。
     */
    private String parserStatus;

    /**
     * 解析失败信息。
     */
    private String errorMessage;
}
