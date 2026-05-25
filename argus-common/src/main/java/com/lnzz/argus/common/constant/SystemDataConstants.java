package com.lnzz.argus.common.constant;

/**
 * 系统数据层通用常量。
 * <p>
 * 保存数据库字段的公共约定值，供 Mapper、Service 和元数据填充逻辑复用。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class SystemDataConstants {

    /** 软删除字段值：已删除。 */
    public static final Boolean DELETED = Boolean.TRUE;

    /** 软删除字段值：未删除。 */
    public static final Boolean NOT_DELETED = Boolean.FALSE;

    private SystemDataConstants() {
    }
}
