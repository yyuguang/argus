package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.config.AdminSecurityProperties;
import com.lnzz.argus.system.entity.SysSession;
import com.lnzz.argus.system.mapper.SysSessionMapper;
import com.lnzz.argus.system.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Portal 会话服务。
 * <p>Token 为服务端不透明随机串，数据库仅保存 SHA-256 哈希，便于立即撤销。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysSessionMapper sessionMapper;
    private final AdminSecurityProperties properties;

    /**
     * 签发新会话。
     *
     * @param userId    用户 ID
     * @param clientIp  客户端 IP
     * @param userAgent 浏览器 User-Agent
     * @return 会话签发结果
     */
    @Override
    public SessionService.IssuedSession issue(Long userId, String clientIp, String userAgent) {
        String token = randomToken();
        LocalDateTime now = LocalDateTime.now();
        SysSession session = new SysSession();
        session.setUserId(userId);
        session.setTokenHash(hashToken(token));
        session.setClientIp(clientIp);
        session.setUserAgent(userAgent);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusHours(properties.getTokenExpireHours()));
        session.setRevoked(false);
        session.setLastActiveAt(now);
        sessionMapper.insert(session);
        log.info("签发后台会话: userId={}, sessionId={}, expiresAt={}, clientIp={}",
                userId, session.getId(), session.getExpiresAt(), clientIp);
        return new SessionService.IssuedSession(session.getId(), token, session.getExpiresAt());
    }

    /**
     * 按明文 Token 查找有效会话。
     *
     * @param token 明文 Token
     * @return 有效会话
     */
    @Override
    public Optional<SysSession> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        SysSession session = sessionMapper.selectByTokenHash(hashToken(token));
        if (session == null || Boolean.TRUE.equals(session.getRevoked())
                || session.getExpiresAt() == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (session != null) {
                log.warn("后台会话不可用: sessionId={}, userId={}, revoked={}, expiresAt={}",
                        session.getId(), session.getUserId(), session.getRevoked(), session.getExpiresAt());
            }
            return Optional.empty();
        }
        session.setLastActiveAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        return Optional.of(session);
    }

    /**
     * 撤销单个会话。
     *
     * @param sessionId 会话 ID
     * @param reason    撤销原因
     */
    @Override
    public void revoke(Long sessionId, String reason) {
        if (sessionId == null) {
            return;
        }
        SysSession session = sessionMapper.selectById(sessionId);
        if (session == null || Boolean.TRUE.equals(session.getRevoked())) {
            return;
        }
        session.setRevoked(true);
        session.setRevokedReason(reason);
        sessionMapper.updateById(session);
        log.info("撤销后台会话: userId={}, sessionId={}, reason={}",
                session.getUserId(), sessionId, reason);
    }

    /**
     * 撤销某用户所有未撤销会话。
     *
     * @param userId 用户 ID
     * @param reason 撤销原因
     */
    @Override
    public void revokeByUser(Long userId, String reason) {
        if (userId == null) {
            return;
        }
        int affectedRows = sessionMapper.revokeActiveByUserId(userId, reason);
        log.info("批量撤销后台用户会话: userId={}, affectedRows={}, reason={}", userId, affectedRows, reason);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Token 哈希计算失败", ex);
        }
    }

}
