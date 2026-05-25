package com.lnzz.argus.rule.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.rule.dao.entity.RuleDocument;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @classname: RuleDocumentMapper
 * @author: Fantasy
 * @date: 2026/05/17 22:20
 * @description: 规则文档主表 Mapper，封装规则文档领域的基础查询语义。
 */
@Mapper
public interface RuleDocumentMapper extends BaseMapper<RuleDocument> {

    /**
     * 按文档 ID 查询未软删除规则文档。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/17 22:20
     */
    default RuleDocument selectNonDeletedById(Long documentId) {
        if (documentId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<RuleDocument>()
                .eq(RuleDocument::getId, documentId)
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 按文档编码查询未软删除规则文档。
     *
     * @param documentCode 文档编码
     * @return 规则文档；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/17 22:20
     */
    default RuleDocument selectByDocumentCode(String documentCode) {
        if (!hasText(documentCode)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<RuleDocument>()
                .eq(RuleDocument::getDocumentCode, documentCode)
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 按来源类型、分类和文件名查询未软删除规则文档。
     *
     * @param sourceType 来源类型
     * @param category   规则分类
     * @param fileName   原始文件名
     * @return 规则文档；不存在时返回 null
     */
    default RuleDocument selectBySourceTypeAndFileName(String sourceType, String category, String fileName) {
        if (!hasText(sourceType) || !hasText(category) || !hasText(fileName)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<RuleDocument>()
                .eq(RuleDocument::getSourceType, sourceType)
                .eq(RuleDocument::getCategory, category)
                .eq(RuleDocument::getFileName, fileName)
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(RuleDocument::getId)
                .last("limit 1"));
    }

    /**
     * 查询指定作用域下的未软删除规则文档列表。
     *
     * @param scope       作用域
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @param status      文档状态，可为空
     * @return 规则文档列表
     * @author Fantasy
     * @date 2026/05/17 22:20
     */
    default List<RuleDocument> selectByScope(String scope, Long scmConfigId, String status) {
        return selectList(new LambdaQueryWrapper<RuleDocument>()
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(hasText(scope), RuleDocument::getScope, scope)
                .eq(scmConfigId != null, RuleDocument::getScmConfigId, scmConfigId)
                .eq(hasText(status), RuleDocument::getStatus, status)
                .orderByDesc(RuleDocument::getUpdateTime)
                .orderByDesc(RuleDocument::getId));
    }

    /**
     * 分页查询规则文档列表。
     *
     * @param page         分页对象
     * @param category     规范分类
     * @param scope        作用域
     * @param scmConfigId  SCM 仓库配置 ID
     * @param status       文档状态
     * @param parseStatus  解析状态
     * @param vectorStatus 向量化状态
     * @param keyword      关键字
     * @return 规则文档分页结果
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    default Page<RuleDocument> selectPageByQuery(Page<RuleDocument> page,
                                                 String category,
                                                 String scope,
                                                 Long scmConfigId,
                                                 String status,
                                                 String parseStatus,
                                                 String vectorStatus,
                                                 String keyword) {
        return selectPage(page, new LambdaQueryWrapper<RuleDocument>()
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(hasText(category), RuleDocument::getCategory, category)
                .eq(hasText(scope), RuleDocument::getScope, scope)
                .eq(scmConfigId != null, RuleDocument::getScmConfigId, scmConfigId)
                .eq(hasText(status), RuleDocument::getStatus, status)
                .eq(hasText(parseStatus), RuleDocument::getParseStatus, parseStatus)
                .eq(hasText(vectorStatus), RuleDocument::getVectorStatus, vectorStatus)
                .and(hasText(keyword), wrapper -> wrapper
                        .like(RuleDocument::getDocumentName, keyword)
                        .or()
                        .like(RuleDocument::getDocumentCode, keyword)
                        .or()
                        .like(RuleDocument::getFileName, keyword))
                .orderByDesc(RuleDocument::getUpdateTime)
                .orderByDesc(RuleDocument::getId));
    }

    /**
     * 更新规则文档状态。
     *
     * @param documentId 文档 ID
     * @param status     新状态
     * @param operator   当前操作者
     * @return 受影响行数
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    default int updateStatusById(Long documentId, String status, String operator) {
        if (documentId == null || !hasText(status)) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<RuleDocument>()
                .eq(RuleDocument::getId, documentId)
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(RuleDocument::getStatus, status)
                .set(RuleDocument::getUpdateBy, operator)
                .set(RuleDocument::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    /**
     * 更新规则文档分块数量和向量化状态。
     *
     * @param documentId   文档 ID
     * @param chunkCount   分块数量
     * @param vectorStatus 向量化状态
     * @param operator     当前操作者
     * @return 受影响行数
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    default int updateChunkCountAndVectorStatus(Long documentId,
                                                Integer chunkCount,
                                                String vectorStatus,
                                                String operator) {
        if (documentId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<RuleDocument>()
                .eq(RuleDocument::getId, documentId)
                .eq(RuleDocument::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(chunkCount != null, RuleDocument::getChunkCount, chunkCount)
                .set(hasText(vectorStatus), RuleDocument::getVectorStatus, vectorStatus)
                .set(RuleDocument::getUpdateBy, operator)
                .set(RuleDocument::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
