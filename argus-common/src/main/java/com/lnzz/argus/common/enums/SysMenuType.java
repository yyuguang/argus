package com.lnzz.argus.common.enums;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import lombok.Getter;

/**
 * 系统菜单类型。
 * <p>数据库保存语义清晰的枚举，前端菜单管理页使用 0/1。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Getter
public enum SysMenuType {

    /** 目录节点，对应 vue-element-plus-admin 中的 Layout 或 ParentLayout。 */
    DIRECTORY(0),

    /** 页面节点，对应真实 views 组件。 */
    MENU(1);

    private final int apiValue;

    SysMenuType(int apiValue) {
        this.apiValue = apiValue;
    }

    public static String fromApi(Integer value) {
        if (value == null || value == 1) {
            return MENU.name();
        }
        if (value == 0) {
            return DIRECTORY.name();
        }
        throw new BizException(ResultCode.PARAM_ERROR, "菜单类型仅支持 0-目录 或 1-菜单");
    }

    public static int toApi(String value) {
        return DIRECTORY.name().equalsIgnoreCase(value) ? DIRECTORY.apiValue : MENU.apiValue;
    }

    public static boolean directory(String value) {
        return DIRECTORY.name().equalsIgnoreCase(value);
    }
}
