package com.lnzz.argus.codeindex.dto.req;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: SourceLocateReqDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码定位请求，用于通过应用版本绑定、类名或文件路径定位源码文件。
 */
@Data
public class SourceLocateReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 环境标识，如 dev/test/staging/prod。
     */
    private String environment;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 分支名称。
     */
    private String branchName;

    /**
     * 提交号。
     */
    private String commitSha;

    /**
     * 全限定类名。
     */
    private String qualifiedName;

    /**
     * 文件路径。
     */
    private String filePath;

    /**
     * 目标行号。
     */
    private Integer lineNumber;
}
