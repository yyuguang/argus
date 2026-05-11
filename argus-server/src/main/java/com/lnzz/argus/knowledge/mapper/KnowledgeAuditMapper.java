package com.lnzz.argus.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.knowledge.entity.KnowledgeAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识操作留痕 Mapper（M8-A04）
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface KnowledgeAuditMapper extends BaseMapper<KnowledgeAudit> {

    /**
     * 查询指定条目的所有操作记录
     */
    @Select("SELECT * FROM argus_knowledge_audit WHERE knowledge_entry_id = #{entryId} ORDER BY create_time DESC")
    List<KnowledgeAudit> findByEntryId(@Param("entryId") Long entryId);
}
