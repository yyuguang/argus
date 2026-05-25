package com.lnzz.argus.codeindex.dto.req;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * @classname: CodeClassPageReqDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: Java 类型索引分页查询请求，支持按包、类名、模块、文件路径和解析状态筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeClassPageReqDTO extends BasePageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源码索引 ID。
     */
    private Long indexId;

    /**
     * 模块路径。
     */
    private String modulePath;

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
     * 文件路径。
     */
    private String filePath;

    /**
     * Java 类型：CLASS/INTERFACE/ENUM/ANNOTATION/RECORD。
     */
    private String classKind;

    /**
     * 解析状态。
     */
    private String parserStatus;

    /**
     * 置信度：HIGH/MEDIUM/LOW/NONE。
     */
    private String confidence;
}
