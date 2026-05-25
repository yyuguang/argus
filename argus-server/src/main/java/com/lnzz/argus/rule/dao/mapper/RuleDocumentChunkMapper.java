package com.lnzz.argus.rule.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @classname: RuleDocumentChunkMapper
 * @author: Fantasy
 * @date: 2026/05/17 22:20
 * @description: 规则文档分块 Mapper，封装分块查询和重建前清理的基础数据访问语义。
 */
@Mapper
public interface RuleDocumentChunkMapper extends BaseMapper<RuleDocumentChunk> {

    /**
     * 查询指定文档下全部未软删除分块，并按分块序号升序返回。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档分块列表
     * @author Fantasy
     * @date 2026/05/17 22:20
     */
    default List<RuleDocumentChunk> listNonDeletedByDocumentId(Long documentId) {
        if (documentId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<RuleDocumentChunk>()
                .eq(RuleDocumentChunk::getDocumentId, documentId)
                .eq(RuleDocumentChunk::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(RuleDocumentChunk::getChunkNo)
                .orderByAsc(RuleDocumentChunk::getId));
    }

    /**
     * 软删除指定文档下全部未删除分块。
     *
     * @param documentId 规则文档 ID
     * @param operator   当前操作者
     * @return 受影响行数
     * @author Fantasy
     * @date 2026/05/17 22:20
     */
    default int softDeleteByDocumentId(Long documentId, String operator) {
        if (documentId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<RuleDocumentChunk>()
                .eq(RuleDocumentChunk::getDocumentId, documentId)
                .eq(RuleDocumentChunk::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(RuleDocumentChunk::getIsDeleted, SystemDataConstants.DELETED)
                .set(RuleDocumentChunk::getUpdateBy, operator)
                .set(RuleDocumentChunk::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    /**
     * 物理删除指定文档下全部分块。
     *
     * <p>分块重建需要重新从 1 开始生成 chunkNo，而唯一索引仅包含
     * `document_id + chunk_no`，因此重建场景必须先物理清空旧分块，
     * 否则软删除记录仍会占用唯一键。</p>
     *
     * @param documentId 规则文档 ID
     * @return 删除行数
     * @author Fantasy
     * @date 2026/05/17 21:35
     */
    default int hardDeleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return 0;
        }
        return delete(new LambdaQueryWrapper<RuleDocumentChunk>()
                .eq(RuleDocumentChunk::getDocumentId, documentId));
    }
}
