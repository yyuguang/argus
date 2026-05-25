package com.lnzz.argus.common.enums;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import lombok.Getter;

/**
 * 系统管理域通用状态。
 * <p>
 * 数据库存储清晰枚举值，前端 API 为兼容 vue-element-plus-admin 使用 1/0。
 * 统一在这里转换，避免 Controller 和 Service 中散落魔法数字。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Getter
public enum SysStatus {

    /** 启用 */
    ENABLED(1),

    /** 禁用 */
    DISABLED(0),

    /** 锁定，接口层按禁用展示 */
    LOCKED(0);

    private final int apiValue;

    SysStatus(int apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * 将前端 1/0 状态转换为数据库枚举。
     *
     * @param value 前端状态值
     * @return 数据库枚举名称
     */
    public static String fromApi(Integer value) {
        if (value == null || value == 1) {
            return ENABLED.name();
        }
        if (value == 0) {
            return DISABLED.name();
        }
        throw new BizException(ResultCode.PARAM_ERROR, "状态仅支持 1-启用 或 0-禁用");
    }

    /**
     * 将数据库枚举转换为前端 1/0 状态。
     *
     * @param value 数据库状态值
     * @return 前端状态值
     */
    public static int toApi(String value) {
        if (ENABLED.name().equalsIgnoreCase(value)) {
            return ENABLED.apiValue;
        }
        return DISABLED.apiValue;
    }

    /**
     * 判断状态是否允许参与登录、授权和动态路由计算。
     *
     * @param value 数据库状态值
     * @return true 表示启用
     */
    public static boolean enabled(String value) {
        return ENABLED.name().equalsIgnoreCase(value);
    }
}
