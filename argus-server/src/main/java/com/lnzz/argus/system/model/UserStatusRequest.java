package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 用户启停请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record UserStatusRequest(List<String> ids, Integer status, String reason) {
}
