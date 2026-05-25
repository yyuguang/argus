package com.lnzz.argus.system.support;

import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.request.BasePageRequest;
import com.lnzz.argus.common.result.ResultCode;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 系统管理模块无状态辅助方法。
 * <p>
 * 该类只保存字段转换、分页兜底、ID 解析等模块内通用逻辑，不承载业务编排职责。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class SystemAdminSupport {

    /** 软删除标识：已删除。 */
    public static final Boolean DELETED = SystemDataConstants.DELETED;

    /** 软删除标识：未删除。 */
    public static final Boolean NOT_DELETED = SystemDataConstants.NOT_DELETED;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SystemAdminSupport() {
    }

    /**
     * 将本地时间格式化为后台 API 统一字符串。
     *
     * @param value 本地时间
     * @return yyyy-MM-dd HH:mm:ss 格式字符串，入参为空时返回 null
     */
    public static String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    /**
     * 去除字符串首尾空白，空字符串统一转为 null。
     *
     * @param value 原始字符串
     * @return 清理后的字符串或 null
     */
    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析接口传入的字符串 ID。
     *
     * @param value     字符串 ID
     * @param fieldName 字段名称，用于错误提示
     * @return Long ID，空字符串返回 null
     */
    public static Long parseId(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, fieldName + "必须是数字");
        }
    }

    /**
     * 批量解析接口传入的字符串 ID 列表。
     *
     * @param ids       字符串 ID 列表
     * @param fieldName 字段名称，用于错误提示
     * @return Long ID 列表
     */
    public static List<Long> parseIds(List<String> ids, String fieldName) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(id -> parseId(id, fieldName))
                .toList();
    }

    /**
     * 将 Long ID 转换为接口返回的字符串 ID。
     *
     * @param id Long ID
     * @return 字符串 ID，入参为空时返回 null
     */
    public static String stringId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 归一化分页页码，最小值为 1。
     *
     * @param pageNo 接口页码
     * @return 可用于 MyBatis Plus Page 的页码
     */
    public static int pageNo(Integer pageNo) {
        return pageNo == null || pageNo < BasePageRequest.DEFAULT_PAGE_NO ? BasePageRequest.DEFAULT_PAGE_NO : pageNo;
    }

    /**
     * 归一化分页大小，默认 10，最大 200。
     *
     * @param pageSize 接口每页大小
     * @return 可用于 MyBatis Plus Page 的每页大小
     */
    public static int pageSize(Integer pageSize) {
        if (pageSize == null || pageSize < BasePageRequest.DEFAULT_PAGE_NO) {
            return BasePageRequest.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, BasePageRequest.MAX_PAGE_SIZE);
    }

    /**
     * 判断业务数据是否已软删除。
     *
     * @param isDeleted 数据库 is_deleted 字段值
     * @return true 表示已删除
     */
    public static boolean deleted(Boolean isDeleted) {
        return Boolean.TRUE.equals(isDeleted);
    }

    /**
     * 判断业务数据是否未软删除。
     *
     * @param isDeleted 数据库 is_deleted 字段值
     * @return true 表示未删除
     */
    public static boolean notDeleted(Boolean isDeleted) {
        return !deleted(isDeleted);
    }
}
