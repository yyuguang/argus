package com.lnzz.argus.codeindex.dto.req;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * @classname: CodeIndexPageReqDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码索引分页查询请求，承载仓库、分支、扫描状态和关键字筛选条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeIndexPageReqDTO extends BasePageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 分支名称。
     */
    private String branchName;

    /**
     * 扫描状态：PENDING/RUNNING/SUCCESS/FAILED。
     */
    private String scanStatus;

    /**
     * 是否仅查询过期索引。
     */
    private Boolean stale;

    /**
     * 关键字，匹配仓库名、分支、提交号或最近错误信息。
     */
    private String keyword;
}
