package com.lnzz.argus.codeindex.dto.req;

import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: CodeIndexScanTaskCreateReqDTO
 * @author: Fantasy
 * @date: 2026/05/25 08:45
 * @description: 源码索引异步扫描任务创建请求，承载管理端刷新索引和强制重建入参。
 */
@Data
public class CodeIndexScanTaskCreateReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分支名称，未传时由 SCM 配置默认分支兜底。
     */
    private String branchName;

    /**
     * 指定提交号，未传时由执行阶段解析分支最新提交。
     */
    private String commitSha;

    /**
     * 扫描类型：FULL/INCREMENTAL/MODULE_RESCAN/REBUILD。
     */
    @NotBlank(message = "scanType 不能为空")
    private String scanType = CodeIndexConstants.ScanType.FULL;

    /**
     * 是否强制重建索引。
     */
    private Boolean forceRebuild = false;

    /**
     * 触发原因，便于审计和问题排查。
     */
    private String reason;
}
