package com.lnzz.argus.system.model;

/**
 * 用户导入单行错误。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record UserImportError(int rowNo, String message) {
}
