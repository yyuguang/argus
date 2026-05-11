package com.lnzz.argus.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "success"),

    /** 参数校验失败 */
    PARAM_ERROR(10001, "参数校验失败"),

    /** 资源不存在 */
    NOT_FOUND(10002, "资源不存在"),

    /** 认证/鉴权失败 */
    UNAUTHORIZED(10003, "认证/鉴权失败"),

    /** SCM API 调用失败 */
    SCM_ERROR(20001, "SCM API 调用失败"),

    /** AI 模型调用失败 */
    AI_MODEL_ERROR(20002, "AI 模型调用失败"),

    /** AI 模型响应解析失败 */
    AI_PARSE_ERROR(20003, "AI 模型响应解析失败"),

    /** 通知发送失败 */
    NOTIFICATION_ERROR(30001, "通知发送失败"),

    /** 知识库操作失败 */
    KNOWLEDGE_ERROR(40001, "知识库操作失败"),

    /** 系统内部错误 */
    SYSTEM_ERROR(50001, "系统内部错误");

    /** 错误码 */
    private final int code;

    /** 错误信息 */
    private final String message;
}
