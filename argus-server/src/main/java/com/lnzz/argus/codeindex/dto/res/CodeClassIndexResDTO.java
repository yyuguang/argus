package com.lnzz.argus.codeindex.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: CodeClassIndexResDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: Java 类型索引响应，表达单个类、接口、枚举、注解或 Record 的源码定位信息。
 */
@Data
public class CodeClassIndexResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 类型索引 ID。
     */
    private Long id;

    /**
     * 源码索引 ID。
     */
    private Long indexId;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 模块路径。
     */
    private String modulePath;

    /**
     * 源码根路径。
     */
    private String sourceRoot;

    /**
     * 文件路径。
     */
    private String filePath;

    /**
     * 文件内容哈希。
     */
    private String fileSha;

    /**
     * 包名。
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
     * 起始行号。
     */
    private Integer lineStart;

    /**
     * 结束行号。
     */
    private Integer lineEnd;

    /**
     * imports JSON。
     */
    private String importsJson;

    /**
     * 解析状态。
     */
    private String parserStatus;

    /**
     * 定位置信度。
     */
    private String confidence;
}
