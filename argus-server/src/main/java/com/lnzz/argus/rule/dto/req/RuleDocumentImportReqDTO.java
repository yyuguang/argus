package com.lnzz.argus.rule.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: RuleDocumentImportReqDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 规则文档导入请求，承载导入元信息并与上传文件对象解耦。
 */
@Data
public class RuleDocumentImportReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规范分类。
     */
    @NotBlank
    private String category;

    /**
     * 作用域：GLOBAL/SCM。
     */
    @NotBlank
    private String scope;

    /**
     * SCM 仓库配置 ID，scope=SCM 时使用。
     */
    private Long scmConfigId;

    /**
     * 文档名称。
     */
    @NotBlank
    private String documentName;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 导入完成后是否立即启用。
     */
    private Boolean activeAfterImport;

    /**
     * 来源类型，默认 UPLOAD；历史迁移任务可写入 MIGRATION。
     */
    private String sourceType;
}
