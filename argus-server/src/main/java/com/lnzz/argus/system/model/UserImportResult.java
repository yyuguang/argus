package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 用户导入结果。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record UserImportResult(long successCount, long failedCount, List<UserImportError> errors) {
}
