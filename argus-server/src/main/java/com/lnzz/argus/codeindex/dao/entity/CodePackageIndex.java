package com.lnzz.argus.codeindex.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: CodePackageIndex
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: Java 包源码索引实体，记录 package 在模块中的分布和歧义状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_code_package_index")
public class CodePackageIndex extends BaseEntity {

    /**
     * 仓库源码索引 ID。
     */
    private Long indexId;

    /**
     * SCM 配置 ID。
     */
    private Long scmConfigId;

    /**
     * Java package 名称。
     */
    private String packageName;

    /**
     * package 分布的模块路径 JSON。
     */
    private String modulePaths;

    /**
     * 自动推断的主模块路径。
     */
    private String primaryModulePath;

    /**
     * 该包下类型数量。
     */
    private Integer classCount;

    /**
     * 是否存在多模块歧义。
     */
    private Boolean ambiguous;

    /**
     * 推断置信度：HIGH/MEDIUM/LOW。
     */
    private String confidence;

    /**
     * 是否软删除。
     */
    @TableLogic(value = "0", delval = "1")
    private Boolean isDeleted;

    /**
     * 乐观锁版本。
     */
    @Version
    private Integer version;
}
