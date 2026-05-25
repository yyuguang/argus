package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @classname: PromptTemplateSchemeResDTO
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板方案响应，承载当前作用域方案、最终生效来源和模板定义元信息。
 */
@Data
public class PromptTemplateSchemeResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板编码。
     */
    private String templateCode;

    /**
     * 模板名称。
     */
    private String templateName;

    /**
     * 一级分类编码。
     */
    private String category;

    /**
     * 模板场景：MAIN / REPAIR / OTHER。
     */
    private String templateScene;

    /**
     * 是否支持仓库级覆盖。
     */
    private Boolean supportScmOverride;

    /**
     * 模板描述。
     */
    private String description;

    /**
     * 当前读取作用域：GLOBAL / SCM。
     */
    private String currentScope;

    /**
     * 当前读取的仓库配置 ID，GLOBAL 固定为 0。
     */
    private Long currentScmConfigId;

    /**
     * 当前作用域方案正文。
     */
    private String contentText;

    /**
     * 当前作用域方案备注。
     */
    private String remark;

    /**
     * 当前作用域方案状态。
     */
    private String status;

    /**
     * 最终生效来源：GLOBAL / SCM。
     */
    private String effectiveScope;

    /**
     * 最终生效的仓库配置 ID，GLOBAL 固定为 0。
     */
    private Long effectiveScmConfigId;

    /**
     * 当前模板是否存在仓库级覆盖。
     */
    private Boolean hasScmOverride;

    /**
     * 最近更新人。
     */
    private String updateBy;

    /**
     * 最近更新时间。
     */
    private LocalDateTime updateTime;
}
