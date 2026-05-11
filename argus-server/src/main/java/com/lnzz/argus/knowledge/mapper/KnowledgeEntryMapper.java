package com.lnzz.argus.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
