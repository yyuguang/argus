package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 菜单启停请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record MenuStatusRequest(List<String> ids, Integer status) {
}
