package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 批量 ID 请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record BatchIdsRequest(List<String> ids) {
}
