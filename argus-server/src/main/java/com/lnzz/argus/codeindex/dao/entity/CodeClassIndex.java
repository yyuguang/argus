package com.lnzz.argus.codeindex.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: CodeClassIndex
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: Java 类型源码索引实体，提供 FQN 到 SCM 文件路径的精确定位能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_code_class_index")
public class CodeClassIndex extends BaseEntity {

    /**
     * 仓库源码索引 ID。
     */
    private Long indexId;

    /**
     * SCM 配置 ID。
     */
    private Long scmConfigId;

    /**
     * 模块相对仓库路径。
     */
    private String modulePath;

    /**
     * 源码根路径。
     */
    private String sourceRoot;

    /**
     * SCM 仓库相对文件路径。
     */
    private String filePath;

    /**
     * 文件内容 hash。
     */
    private String fileSha;

    /**
     * Java package 名称。
     */
    private String packageName;

    /**
     * 简单类型名。
     */
    private String className;

    /**
     * Java 全限定名。
     */
    private String qualifiedName;

    /**
     * 类型种类：CLASS/INTERFACE/ENUM/ANNOTATION/RECORD。
     */
    private String classKind;

    /**
     * 是否主类型。
     */
    private Boolean primaryType;

    /**
     * 类型起始行。
     */
    private Integer lineStart;

    /**
     * 类型结束行。
     */
    private Integer lineEnd;

    /**
     * import 列表 JSON。
     */
    private String importsJson;

    /**
     * 解析状态：SUCCESS/PARTIAL/FAILED。
     */
    private String parserStatus;

    /**
     * 索引置信度：HIGH/MEDIUM/LOW。
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
