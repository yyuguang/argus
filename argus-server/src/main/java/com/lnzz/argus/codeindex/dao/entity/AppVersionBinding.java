package com.lnzz.argus.codeindex.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @classname: AppVersionBinding
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: 应用环境源码版本绑定实体，维护 appName 与当前部署 commit 的索引关系。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_app_version_binding")
public class AppVersionBinding extends BaseEntity {

    /**
     * ProjectMapping 映射 ID。
     */
    private Long mappingId;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 运行环境，如 dev/test/prod。
     */
    private String environment;

    /**
     * SCM 配置 ID。
     */
    private Long scmConfigId;

    /**
     * 部署分支名称。
     */
    private String branchName;

    /**
     * 部署 commit SHA。
     */
    private String commitSha;

    /**
     * 业务版本号或发布单号。
     */
    private String versionName;

    /**
     * 对应源码索引 ID，可为空表示索引构建中。
     */
    private Long indexId;

    /**
     * 绑定来源：DEPLOY_CALLBACK/APP_REPORT/MANUAL/DEFAULT_BRANCH。
     */
    private String bindingSource;

    /**
     * 是否当前激活版本。
     */
    private Boolean active;

    /**
     * 生效时间。
     */
    private LocalDateTime activatedAt;

    /**
     * 最近一次从运行期事件看到该版本的时间。
     */
    private LocalDateTime lastSeenAt;

    /**
     * 备注。
     */
    private String remark;

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
