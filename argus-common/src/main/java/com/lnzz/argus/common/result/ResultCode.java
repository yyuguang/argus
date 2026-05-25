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

    /** 未携带 Portal 用户 Token */
    ADMIN_UNAUTHENTICATED(40101, "请先登录"),

    /** Portal 用户 Token 已过期或会话已撤销 */
    ADMIN_TOKEN_EXPIRED(40102, "登录已过期"),

    /** Portal 用户账号不可用 */
    ADMIN_ACCOUNT_DISABLED(40301, "账号已禁用"),

    /** Portal 用户缺少当前操作权限 */
    ADMIN_FORBIDDEN(40302, "无权执行该操作"),

    /** 登录账号已存在 */
    ADMIN_ACCOUNT_CONFLICT(40901, "登录账号已存在"),

    /** 角色编码已存在 */
    ADMIN_ROLE_CODE_CONFLICT(40902, "角色编码已存在"),

    /** 菜单路由名称已存在 */
    ADMIN_MENU_NAME_CONFLICT(40903, "菜单路由名称已存在"),

    /** 权限编码已存在 */
    ADMIN_PERMISSION_CODE_CONFLICT(40904, "权限编码已存在"),

    /** 至少保留一个超级管理员 */
    ADMIN_SUPER_ADMIN_REQUIRED(40905, "至少保留一个超级管理员"),

    /** 用户导入文件校验失败 */
    ADMIN_IMPORT_ERROR(42201, "导入文件校验失败"),

    /** 认证/鉴权失败，历史错误码兼容 */
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
