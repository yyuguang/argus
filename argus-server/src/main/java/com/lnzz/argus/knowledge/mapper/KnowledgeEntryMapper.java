package com.lnzz.argus.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 知识条目 Mapper（M8）
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {

    /**
     * M8-A02: 按指纹精确匹配相似条目（已确认 + 白名单）
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE error_fingerprint = #{fingerprint} "
            + "AND status IN ('CONFIRMED', 'WHITELIST') ORDER BY occurrence_count DESC LIMIT #{limit}")
    List<KnowledgeEntry> findByFingerprint(@Param("fingerprint") String fingerprint, @Param("limit") int limit);

    /**
     * 按指纹查找可复用条目（草稿/已确认/白名单），用于避免重复生成草稿。
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE error_fingerprint = #{fingerprint} "
            + "AND status IN ('DRAFT', 'CONFIRMED', 'WHITELIST') "
            + "ORDER BY FIELD(status, 'CONFIRMED', 'WHITELIST', 'DRAFT'), occurrence_count DESC LIMIT 1")
    KnowledgeEntry findReusableByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 查找同应用同类型草稿，用作相似问题合并候选。
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE error_type = #{errorType} "
            + "AND app_name = #{appName} AND status = 'DRAFT' "
            + "ORDER BY occurrence_count DESC, update_time DESC LIMIT 1")
    KnowledgeEntry findDraftByErrorTypeAndApp(@Param("errorType") String errorType,
                                              @Param("appName") String appName);

    /**
     * 回写知识条目的发生次数和最近发生时间。
     */
    @Update("UPDATE argus_knowledge_entry SET occurrence_count = COALESCE(occurrence_count, 0) + #{delta}, "
            + "last_occurred_at = CASE "
            + "WHEN last_occurred_at IS NULL OR #{lastOccurredAt} > last_occurred_at THEN #{lastOccurredAt} "
            + "ELSE last_occurred_at END, "
            + "source_event_id = COALESCE(source_event_id, #{sourceEventId}), "
            + "source_analysis_id = COALESCE(source_analysis_id, #{sourceAnalysisId}), "
            + "update_time = NOW() WHERE id = #{id}")
    int aggregateKnowledgeOccurrence(@Param("id") Long id,
                                     @Param("delta") int delta,
                                     @Param("lastOccurredAt") LocalDateTime lastOccurredAt,
                                     @Param("sourceEventId") Long sourceEventId,
                                     @Param("sourceAnalysisId") Long sourceAnalysisId);

    /**
     * M8-A02: 按错误类型 + 应用匹配
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE error_type = #{errorType} "
            + "AND app_name = #{appName} AND status IN ('CONFIRMED', 'WHITELIST') "
            + "ORDER BY occurrence_count DESC LIMIT #{limit}")
    List<KnowledgeEntry> findByErrorTypeAndApp(@Param("errorType") String errorType,
                                                @Param("appName") String appName,
                                                @Param("limit") int limit);

    /**
     * M8-A02: 按错误类型匹配（跨应用）
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE error_type = #{errorType} "
            + "AND status IN ('CONFIRMED', 'WHITELIST') "
            + "ORDER BY occurrence_count DESC LIMIT #{limit}")
    List<KnowledgeEntry> findByErrorType(@Param("errorType") String errorType, @Param("limit") int limit);

    /**
     * M8-A02: 关键字模糊匹配（降级策略）
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE status IN ('CONFIRMED', 'WHITELIST') "
            + "AND (error_pattern LIKE CONCAT('%', #{keyword}, '%') "
            + "OR root_cause LIKE CONCAT('%', #{keyword}, '%') "
            + "OR title LIKE CONCAT('%', #{keyword}, '%')) "
            + "ORDER BY occurrence_count DESC LIMIT #{limit}")
    List<KnowledgeEntry> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * M8-A05: 查找高频已确认条目（白名单候选）
     */
    @Select("SELECT * FROM argus_knowledge_entry WHERE status = 'CONFIRMED' "
            + "AND occurrence_count >= #{minCount} ORDER BY occurrence_count DESC")
    List<KnowledgeEntry> findWhitelistCandidates(@Param("minCount") int minCount);
}
