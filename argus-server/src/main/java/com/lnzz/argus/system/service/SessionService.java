package com.lnzz.argus.system.service;

import com.lnzz.argus.system.entity.SysSession;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @classname: SessionService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: Portal 后台服务端会话接口，负责 Token 签发、校验和撤销。
 */
public interface SessionService {

    /**
     * 签发新会话。
     *
     * @param userId    用户 ID
     * @param clientIp  客户端 IP
     * @param userAgent 浏览器 User-Agent
     * @return 会话签发结果
     */
    IssuedSession issue(Long userId, String clientIp, String userAgent);

    /**
     * 按明文 Token 查找有效会话。
     *
     * @param token 明文 Token
     * @return 有效会话
     */
    Optional<SysSession> findValid(String token);

    /**
     * 撤销单个会话。
     *
     * @param sessionId 会话 ID
     * @param reason    撤销原因
     */
    void revoke(Long sessionId, String reason);

    /**
     * 撤销某用户所有未撤销会话。
     *
     * @param userId 用户 ID
     * @param reason 撤销原因
     */
    void revokeByUser(Long userId, String reason);

    /**
     * 登录签发结果。Token 只在响应时返回明文，数据库仅保存哈希。
     */
    record IssuedSession(Long sessionId, String token, LocalDateTime expiresAt) {
    }
}
