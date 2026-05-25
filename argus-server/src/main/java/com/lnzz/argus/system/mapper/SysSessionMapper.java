package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户会话 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysSessionMapper extends BaseMapper<SysSession> {

    /**
     * 按 Token 哈希查询会话。
     *
     * @param tokenHash Token 的 SHA-256 哈希
     * @return 会话记录；不存在时返回 null
     */
    default SysSession selectByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysSession>()
                .eq(SysSession::getTokenHash, tokenHash)
                .last("limit 1"));
    }

    /**
     * 撤销指定用户全部未撤销会话。
     *
     * @param userId 用户 ID
     * @param reason 撤销原因
     * @return 影响行数
     */
    default int revokeActiveByUserId(Long userId, String reason) {
        if (userId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<SysSession>()
                .eq(SysSession::getUserId, userId)
                .eq(SysSession::getRevoked, false)
                .set(SysSession::getRevoked, true)
                .set(SysSession::getRevokedReason, reason));
    }
}
