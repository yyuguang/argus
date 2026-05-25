package com.lnzz.argus.codeindex.dto.req;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: CodeIndexScanReqDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码索引扫描请求，承载手动触发扫描时的分支、提交号和扫描类型。
 */
@Data
public class CodeIndexScanReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分支名称，未传时由 SCM 配置默认分支兜底。
     */
    private String branchName;

    /**
     * 指定提交号，未传时扫描分支最新提交。
     */
    private String commitSha;

    /**
     * 增量扫描基线提交号。
     */
    private String baseCommitSha;

    /**
     * 扫描类型：FULL/INCREMENTAL/MODULE_RESCAN/REBUILD。
     */
    private String scanType;

    /**
     * 是否强制重建索引。
     */
    private Boolean forceRebuild;

    /**
     * 触发原因，便于审计和问题排查。
     */
    private String reason;

    /**
     * 待读取文件路径列表。首期 SCM 适配基于已知路径定向读取。
     */
    private List<String> filePaths = new ArrayList<>();

    /**
     * 已删除文件路径列表，增量扫描时用于排除旧文件。
     */
    private List<String> deletedFilePaths = new ArrayList<>();

    /**
     * 源码根高级覆盖项，仅作为自动发现结果的补充。
     */
    private List<String> sourceRootOverrides = new ArrayList<>();
}
