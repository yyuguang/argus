package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.knowledge.model.ErrorFingerprintSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错误事件 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorEventMapper extends BaseMapper<ErrorEvent> {

    /**
     * 按主键查询错误事件。
     *
     * @param eventId 事件 ID
     * @return 错误事件
     */
    default ErrorEvent findById(Long eventId) {
        return selectById(eventId);
    }

    /**
     * 统计全部错误事件。
     *
     * @return 事件总数
     */
    default long countAll() {
        return selectCount(null);
    }

    /**
     * 分页查询错误事件。
     *
     * @param pageNo      页码
     * @param pageSize    每页数量
     * @param appName     应用名
     * @param environment 环境
     * @param severity    严重度
     * @param status      处理状态
     * @param keyword     关键词
     * @return 错误事件分页
     */
    default Page<ErrorEvent> queryEvents(long pageNo, long pageSize, String appName, String environment,
                                         String severity, String status, String keyword) {
        LambdaQueryWrapper<ErrorEvent> wrapper = new LambdaQueryWrapper<ErrorEvent>()
                .eq(hasText(appName), ErrorEvent::getAppName, appName)
                .eq(hasText(environment), ErrorEvent::getEnvironment, environment)
                .eq(hasText(severity), ErrorEvent::getSeverity, severity)
                .eq(hasText(status), ErrorEvent::getProcessingStatus, status)
                .and(hasText(keyword), query -> query
                        .like(ErrorEvent::getErrorMessage, keyword)
                        .or()
                        .like(ErrorEvent::getErrorFingerprint, keyword)
                        .or()
                        .like(ErrorEvent::getClassName, keyword)
                        .or()
                        .like(ErrorEvent::getInterfaceRef, keyword))
                .orderByDesc(ErrorEvent::getLastOccurredAt)
                .orderByDesc(ErrorEvent::getOccurredAt)
                .orderByDesc(ErrorEvent::getId);
        return selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    /**
     * 查询同指纹错误事件。
     *
     * @param fingerprint 错误指纹
     * @param limit       最大返回数量
     * @return 错误事件列表
     */
    default List<ErrorEvent> findByFingerprint(String fingerprint, int limit) {
        return selectList(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getErrorFingerprint, fingerprint)
                .orderByDesc(ErrorEvent::getLastOccurredAt)
                .orderByDesc(ErrorEvent::getOccurredAt)
                .last("LIMIT " + limit));
    }

    /**
     * 按严重度统计错误事件。
     *
     * @param severity 严重度
     * @return 事件数量
     */
    default long countBySeverity(String severity) {
        return selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getSeverity, severity));
    }

    /**
     * 按处理状态统计错误事件。
     *
     * @param status 处理状态
     * @return 事件数量
     */
    default long countByProcessingStatus(String status) {
        return selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getProcessingStatus, status));
    }

    /**
     * 统计尚未分析的错误事件。
     *
     * @return 尚未分析的事件数量
     */
    default long countUnanalyzed() {
        return selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .and(query -> query.eq(ErrorEvent::getAnalyzed, false).or().isNull(ErrorEvent::getAnalyzed)));
    }

    /**
     * 更新错误事件分析状态。
     *
     * @param eventId 事件 ID
     * @param analyzed 是否已分析
     * @param status   处理状态
     * @return 更新行数
     */
    default int updateAnalysisState(Long eventId, boolean analyzed, String status) {
        return update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getAnalyzed, analyzed)
                .set(ErrorEvent::getProcessingStatus, status));
    }

    /**
     * 更新错误事件处理状态和原因。
     *
     * @param eventId 事件 ID
     * @param status  处理状态
     * @param reason  原因
     * @return 更新行数
     */
    default int updateStatus(Long eventId, String status, String reason) {
        return update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getProcessingStatus, status)
                .set(ErrorEvent::getSeverityReason, reason));
    }

    /**
     * 标记错误事件为误报。
     *
     * @param eventId           事件 ID
     * @param status           处理状态
     * @param analysisDecision 分析决策
     * @param reason           原因
     * @return 更新行数
     */
    default int markFalsePositive(Long eventId, String status, String analysisDecision, String reason) {
        return update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getProcessingStatus, status)
                .set(ErrorEvent::getAnalysisDecision, analysisDecision)
                .set(ErrorEvent::getSeverityReason, reason));
    }

    /**
     * 回写错误事件通知状态。
     *
     * @param eventId 事件 ID
     * @param notified 是否已通知
     * @return 更新行数
     */
    default int updateNotified(Long eventId, boolean notified) {
        return update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getNotified, notified));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 查找指定指纹最近一条事件（用于聚合去重）
     */
    @Select("SELECT * FROM argus_error_event WHERE error_fingerprint = #{fingerprint} "
            + "ORDER BY occurred_at DESC LIMIT 1")
    ErrorEvent findLatestByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 查找指定应用、环境、指纹最近一条事件（用于跨环境隔离聚合）
     */
    @Select("SELECT * FROM argus_error_event WHERE app_name = #{appName} "
            + "AND (environment <=> #{environment}) "
            + "AND error_fingerprint = #{fingerprint} "
            + "ORDER BY occurred_at DESC LIMIT 1")
    ErrorEvent findLatestByAppEnvFingerprint(@Param("appName") String appName,
                                             @Param("environment") String environment,
                                             @Param("fingerprint") String fingerprint);

    /**
     * 聚合更新: 增加出现次数、更新时间、最近业务主键、最近 traceId
     */
    @Update("UPDATE argus_error_event SET occurrence_count = occurrence_count + 1, "
            + "last_occurred_at = #{lastOccurredAt}, "
            + "last_business_key = COALESCE(#{lastBusinessKey}, last_business_key), "
            + "last_trace_id = COALESCE(#{lastTraceId}, last_trace_id), "
            + "processing_status = 'AGGREGATED', "
            + "update_time = NOW() "
            + "WHERE id = #{id}")
    int aggregateOccurrence(@Param("id") Long id,
                            @Param("lastOccurredAt") java.time.LocalDateTime lastOccurredAt,
                            @Param("lastBusinessKey") String lastBusinessKey,
                            @Param("lastTraceId") String lastTraceId);

    /**
     * 按时间窗口查询高频错误指纹。
     */
    @Select("SELECT error_fingerprint, app_name, error_type, severity, source_type, interface_ref, "
            + "COUNT(*) AS event_count, SUM(occurrence_count) AS occurrence_total, "
            + "MIN(COALESCE(first_occurred_at, occurred_at)) AS first_occurred_at, "
            + "MAX(COALESCE(last_occurred_at, occurred_at)) AS last_occurred_at "
            + "FROM argus_error_event "
            + "WHERE COALESCE(last_occurred_at, occurred_at) >= #{since} "
            + "GROUP BY error_fingerprint, app_name, error_type, severity, source_type, interface_ref "
            + "HAVING occurrence_total >= #{minOccurrences} "
            + "ORDER BY occurrence_total DESC, last_occurred_at DESC LIMIT #{limit}")
    List<ErrorFingerprintSummary> findHighFrequencyFingerprints(@Param("since") LocalDateTime since,
                                                                @Param("minOccurrences") int minOccurrences,
                                                                @Param("limit") int limit);

    /**
     * 查询窗口内首次出现的新错误指纹。
     */
    @Select("SELECT error_fingerprint, app_name, error_type, severity, source_type, interface_ref, "
            + "COUNT(*) AS event_count, SUM(occurrence_count) AS occurrence_total, "
            + "MIN(COALESCE(first_occurred_at, occurred_at)) AS first_occurred_at, "
            + "MAX(COALESCE(last_occurred_at, occurred_at)) AS last_occurred_at "
            + "FROM argus_error_event "
            + "GROUP BY error_fingerprint, app_name, error_type, severity, source_type, interface_ref "
            + "HAVING first_occurred_at >= #{since} "
            + "ORDER BY first_occurred_at DESC LIMIT #{limit}")
    List<ErrorFingerprintSummary> findNewFingerprints(@Param("since") LocalDateTime since,
                                                      @Param("limit") int limit);

    /**
     * 查询当前窗口相较上一窗口突增的错误指纹。
     */
    @Select("SELECT cur.error_fingerprint, cur.app_name, cur.error_type, cur.severity, cur.source_type, cur.interface_ref, "
            + "cur.event_count, cur.occurrence_total, COALESCE(prev.occurrence_total, 0) AS previous_occurrence_total, "
            + "(cur.occurrence_total - COALESCE(prev.occurrence_total, 0)) AS increase_total, "
            + "cur.first_occurred_at, cur.last_occurred_at "
            + "FROM ("
            + "  SELECT error_fingerprint, app_name, error_type, severity, source_type, interface_ref, "
            + "  COUNT(*) AS event_count, SUM(occurrence_count) AS occurrence_total, "
            + "  MIN(COALESCE(first_occurred_at, occurred_at)) AS first_occurred_at, "
            + "  MAX(COALESCE(last_occurred_at, occurred_at)) AS last_occurred_at "
            + "  FROM argus_error_event "
            + "  WHERE COALESCE(last_occurred_at, occurred_at) >= #{since} "
            + "  GROUP BY error_fingerprint, app_name, error_type, severity, source_type, interface_ref"
            + ") cur LEFT JOIN ("
            + "  SELECT error_fingerprint, app_name, SUM(occurrence_count) AS occurrence_total "
            + "  FROM argus_error_event "
            + "  WHERE COALESCE(last_occurred_at, occurred_at) >= #{previousSince} "
            + "  AND COALESCE(last_occurred_at, occurred_at) < #{since} "
            + "  GROUP BY error_fingerprint, app_name"
            + ") prev ON cur.error_fingerprint = prev.error_fingerprint AND cur.app_name = prev.app_name "
            + "WHERE (cur.occurrence_total - COALESCE(prev.occurrence_total, 0)) >= #{minIncrease} "
            + "ORDER BY increase_total DESC, cur.last_occurred_at DESC LIMIT #{limit}")
    List<ErrorFingerprintSummary> findSurgingFingerprints(@Param("since") LocalDateTime since,
                                                          @Param("previousSince") LocalDateTime previousSince,
                                                          @Param("minIncrease") int minIncrease,
                                                          @Param("limit") int limit);
}
