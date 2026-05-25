package com.lnzz.argus.rule.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: PromptTemplateDefinition
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板定义实体，承载系统预置的模板组目录、分类和展示信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_prompt_template_definition")
public class PromptTemplateDefinition extends BaseEntity {

    /**
     * 模板编码，系统内唯一。
     */
    private String templateCode;

    /**
     * 模板名称。
     */
    private String templateName;

    /**
     * 一级分类：CODE_REVIEW / ERROR_ANALYSIS。
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
     * 展示顺序。
     */
    private Integer sortNo;

    /**
     * 模板状态：ACTIVE / DISABLED。
     */
    private String status;

    /**
     * 模板描述。
     */
    private String description;

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
