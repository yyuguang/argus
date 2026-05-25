package com.lnzz.argus.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.system.entity.SysSecurityAuditLog;
import com.lnzz.argus.system.mapper.SysSecurityAuditLogMapper;
import com.lnzz.argus.system.model.SecurityAuditLogResponse;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.system.service.SysSecurityAuditQueryService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 安全审计查询服务。
 * <p>只返回审计摘要，避免直接暴露 before/after 快照中的敏感字段。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSecurityAuditQueryServiceImpl implements SysSecurityAuditQueryService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysSecurityAuditLogMapper auditLogMapper;

    /**
     * 分页查询后台安全审计日志。
     *
     * @param actorUsername 操作人账号
     * @param action        操作编码
     * @param resourceType  资源类型
     * @param result        审计结果
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @return 审计日志分页结果
     */
    @Override
    public PageResult<SecurityAuditLogResponse> page(String actorUsername,
                                                    String action,
                                                    String resourceType,
                                                    String result,
                                                    String startTime,
                                                    String endTime,
                                                    Integer pageNo,
                                                    Integer pageSize) {
        int normalizedPageNo = SystemAdminSupport.pageNo(pageNo);
        int normalizedPageSize = SystemAdminSupport.pageSize(pageSize);
        Page<SysSecurityAuditLog> page = auditLogMapper.selectAdminPage(
                new Page<>(normalizedPageNo, normalizedPageSize),
                actorUsername,
                action,
                resourceType,
                result,
                parse(startTime),
                parse(endTime));
        PageResult<SecurityAuditLogResponse> resultData = PageResult.of(
                page.getRecords().stream()
                        .map(this::toResponse)
                        .toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal());
        log.debug("查询安全审计日志: actorUsername={}, action={}, resourceType={}, result={}, total={}",
                actorUsername, action, resourceType, result, page.getTotal());
        return resultData;
    }

    private SecurityAuditLogResponse toResponse(SysSecurityAuditLog log) {
        return new SecurityAuditLogResponse(
                String.valueOf(log.getId()),
                log.getActorUsername(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getResult(),
                log.getClientIp(),
                log.getTraceId(),
                SystemAdminSupport.format(log.getCreateTime()));
    }

    private LocalDateTime parse(String value) {
        return StringUtils.hasText(value) ? LocalDateTime.parse(value.trim(), FORMATTER) : null;
    }
}
