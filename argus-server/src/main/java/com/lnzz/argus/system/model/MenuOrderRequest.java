package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 菜单排序请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record MenuOrderRequest(List<MenuOrderItem> items) {
}
