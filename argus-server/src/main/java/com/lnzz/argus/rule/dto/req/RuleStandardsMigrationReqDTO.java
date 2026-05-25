package com.lnzz.argus.rule.dto.req;

import lombok.Data;

/**
 * 历史 standards 迁移请求。
 *
 * @author Fantasy
 * @date 2026/05/17 21:40
 */
@Data
public class RuleStandardsMigrationReqDTO {

    /**
     * 导入后是否立即启用。
     */
    private Boolean activeAfterImport;
}
