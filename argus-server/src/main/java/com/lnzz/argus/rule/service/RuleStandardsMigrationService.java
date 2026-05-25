package com.lnzz.argus.rule.service;

import com.lnzz.argus.rule.dto.res.RuleStandardsMigrationResDTO;

/**
 * @classname: RuleStandardsMigrationService
 * @author: Fantasy
 * @date: 2026/05/17 21:29
 * @description: 历史 standards 目录迁移服务，负责将旧规范文件一次性导入规则管理域。
 */
public interface RuleStandardsMigrationService {

    /**
     * 执行历史 standards 目录迁移。
     *
     * @param activeAfterImport 导入后是否立即启用
     * @return 迁移结果
     */
    RuleStandardsMigrationResDTO migrateHistoricalStandards(Boolean activeAfterImport);
}
