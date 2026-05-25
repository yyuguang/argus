package com.lnzz.argus.scm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.scm.entity.ScmConfig;

import java.util.List;

/**
 * SCM 平台配置服务接口
 * <p>管理 GitLab / GitHub / Gitee 等代码托管平台的连接配置</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ScmConfigService {

    /**
     * 分页查询 SCM 配置。
     *
     * @param pageNo      页码
     * @param pageSize    每页大小
     * @param scmProvider SCM 提供方
     * @param enabled     启用状态
     * @param keyword     关键字
     * @return SCM 配置分页结果
     */
    Page<ScmConfig> pageConfigs(int pageNo, int pageSize, String scmProvider, Boolean enabled, String keyword);

    /**
     * 查询所有 SCM 配置
     *
     * @return 配置列表，按更新时间倒序
     */
    List<ScmConfig> listAll();

    /**
     * 按主键查询
     *
     * @param id 配置ID
     * @return 配置实体，不存在返回 null
     */
    ScmConfig getById(Long id);

    /**
     * 按主键查询，不存在则抛异常
     *
     * @param id 配置ID
     * @return 配置实体
     * @throws com.lnzz.argus.common.exception.BizException 配置不存在时抛出 NOT_FOUND
     */
    ScmConfig requireById(Long id);

    /**
     * 按平台和仓库信息匹配配置
     * <p>优先按 projectId 精确匹配，其次按 repoOwner + repoName 匹配，仅返回已启用的配置</p>
     *
     * @param provider  SCM 平台（gitlab / github / gitee）
     * @param projectId 项目ID（可选）
     * @param repoOwner 仓库所有者（可选，与 repoName 配合使用）
     * @param repoName  仓库名（可选，与 repoOwner 配合使用）
     * @return 匹配的配置，未匹配返回 null
     */
    ScmConfig resolveConfig(String provider, Long projectId, String repoOwner, String repoName);

    /**
     * 新增或更新配置
     * <p>新增时 id 为 null，直接插入。更新时如未提供 token/secret 则保留原有值不覆盖</p>
     * <p>自动规范化：scmProvider 转小写、enabled 默认 true、maxRelatedClasses 默认 5、maxContextTokens 默认 16000</p>
     *
     * @param config 配置实体
     * @return 保存后的完整配置
     */
    ScmConfig saveOrUpdate(ScmConfig config);

    /**
     * 脱敏 secret/token 用于前端展示
     * <p>长度 ≤ 8 时全部替换为星号，否则保留前后各 4 位</p>
     *
     * @param secret 原始密钥
     * @return 脱敏后的字符串
     */
    String maskSecret(String secret);
}
