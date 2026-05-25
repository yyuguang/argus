package com.lnzz.argus.scm.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SCM 配置分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmConfigPageRequest extends BasePageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SCM 提供方: gitlab/github/gitee。 */
    private String scmProvider;

    /** 是否启用。 */
    private Boolean enabled;

    /** 关键字，匹配项目名、仓库归属、仓库名、API 地址和说明。 */
    private String keyword;
}
