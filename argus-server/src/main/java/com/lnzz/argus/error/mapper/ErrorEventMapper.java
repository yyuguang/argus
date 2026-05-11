package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 错误事件 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorEventMapper extends BaseMapper<ErrorEvent> {

    /**
     * 查找指定指纹最近一条事件（用于聚合去重）
     */
    @Select("SELECT * FROM argus_error_event WHERE error_fingerprint = #{fingerprint} "
            + "ORDER BY occurred_at DESC LIMIT 1")
    ErrorEvent findLatestByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 聚合更新: 增加出现次数、更新时间、最近业务主键、最近 traceId
     */
    @Update("UPDATE argus_error_event SET occurrence_count = occurrence_count + 1, "
            + "last_occurred_at = #{lastOccurredAt}, "
            + "last_business_key = COALESCE(#{lastBusinessKey}, last_business_key), "
            + "last_trace_id = COALESCE(#{lastTraceId}, last_trace_id), "
            + "updated_at = NOW() "
            + "WHERE id = #{id}")
    int aggregateOccurrence(@Param("id") Long id,
                            @Param("lastOccurredAt") java.time.LocalDateTime lastOccurredAt,
                            @Param("lastBusinessKey") String lastBusinessKey,
                            @Param("lastTraceId") String lastTraceId);
}
