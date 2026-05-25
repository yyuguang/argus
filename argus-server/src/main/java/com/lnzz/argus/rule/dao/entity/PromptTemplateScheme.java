package com.lnzz.argus.rule.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: PromptTemplateScheme
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板方案实体，承载全局兜底方案和仓库级覆盖方案正文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_prompt_template_scheme")
public class PromptTemplateScheme extends BaseEntity {

    /**
     * 模板编码，关联 Prompt 模板定义。
     */
    private String templateCode;

    /**
     * 作用域：GLOBAL / SCM。
     */
    private String scope;

    /**
     * 仓库配置 ID，GLOBAL 固定为 0。
     */
    private Long scmConfigId;

    /**
     * 模板正文。
     */
    private String contentText;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 方案状态：ACTIVE / DISABLED。
     */
    private String status;

    /**
     * 是否软删除。
     */
    private Boolean isDeleted;

    /**
     * 乐观锁版本。
     */
    @Version
    private Integer version;
}
