package com.lnzz.argus.error.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Agent 批量推送请求
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class BatchLogRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Agent 实例标识 */
    @NotEmpty(message = "agentId 不能为空")
    private String agentId;

    /** 本批次推送条目，至少1条 */
    @NotEmpty(message = "entries 不能为空")
    @Valid
    private List<ErrorLogEntry> entries;
}
