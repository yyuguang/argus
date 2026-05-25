package com.lnzz.argus.codeindex.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: CodeModuleIndex
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: 模块源码索引实体，记录一次仓库索引中识别出的 Maven 或目录模块。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_code_module_index")
public class CodeModuleIndex extends BaseEntity {

    /**
     * 仓库源码索引 ID。
     */
    private Long indexId;

    /**
     * SCM 配置 ID。
     */
    private Long scmConfigId;

    /**
     * 模块名称，优先使用 Maven artifactId。
     */
    private String moduleName;

    /**
     * 模块相对仓库路径。
     */
    private String modulePath;

    /**
     * 父模块相对仓库路径。
     */
    private String parentModulePath;

    /**
     * 构建类型：MAVEN/DISCOVERED/UNKNOWN。
     */
    private String buildType;

    /**
     * Maven packaging，如 jar/war/pom。
     */
    private String packaging;

    /**
     * 源码根列表 JSON。
     */
    private String sourceRoots;

    /**
     * Java 文件数量。
     */
    private Integer javaFileCount;

    /**
     * 类型数量。
     */
    private Integer classCount;

    /**
     * 模块扫描状态：SUCCESS/PARTIAL/FAILED。
     */
    private String scanStatus;

    /**
     * 模块扫描告警。
     */
    private String warningMessage;

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
