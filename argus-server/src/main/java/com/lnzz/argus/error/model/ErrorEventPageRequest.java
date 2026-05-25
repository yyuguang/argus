package com.lnzz.argus.error.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 错误事件分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ErrorEventPageRequest extends BasePageRequest {

    /** 应用名称。 */
    private String appName;

    /** 运行环境。 */
    private String environment;

    /** 严重等级。 */
    private String severity;

    /** 处理状态。 */
    private String status;

    /** 关键字，匹配异常摘要或错误信息。 */
    private String keyword;
}
