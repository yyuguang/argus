package com.lnzz.argus.rule.dto.req;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: PromptTemplateSaveReqDTO
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板方案保存请求，支持全局兜底和仓库级覆盖两类保存语义。
 */
@Data
public class PromptTemplateSaveReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板编码。
     */
    private String templateCode;

    /**
     * 作用域：GLOBAL / SCM。
     */
    private String scope;

    /**
     * 仓库配置 ID，GLOBAL 固定为 0。
     */
    private Long scmConfigId;

    /**
     * 模板正文。
     */
    private String contentText;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 方案状态：ACTIVE / DISABLED。
     */
    private String status;
}
