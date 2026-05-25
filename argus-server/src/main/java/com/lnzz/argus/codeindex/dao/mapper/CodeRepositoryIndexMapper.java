package com.lnzz.argus.codeindex.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Mapper;

/**
 * @classname: CodeRepositoryIndexMapper
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: 仓库源码索引主表 Mapper，封装索引快照基础查询语义。
 */
@Mapper
public interface CodeRepositoryIndexMapper extends BaseMapper<CodeRepositoryIndex> {

    /**
     * 按仓库、commit 和索引结构版本查询未软删除索引快照。
     *
     * @param scmConfigId  SCM 配置 ID
     * @param commitSha    commit SHA
     * @param indexVersion 索引结构版本
     * @return 源码索引快照；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default CodeRepositoryIndex selectByCommit(Long scmConfigId, String commitSha, Integer indexVersion) {
        if (scmConfigId == null || !hasText(commitSha) || indexVersion == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<CodeRepositoryIndex>()
                .eq(CodeRepositoryIndex::getScmConfigId, scmConfigId)
                .eq(CodeRepositoryIndex::getCommitSha, commitSha)
                .eq(CodeRepositoryIndex::getIndexVersion, indexVersion)
                .eq(CodeRepositoryIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 查询指定仓库和分支下最近一次成功的未过期索引。
     *
     * @param scmConfigId SCM 配置 ID
     * @param branchName  分支名称
     * @return 最近成功索引；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default CodeRepositoryIndex selectLatestSuccessful(Long scmConfigId, String branchName) {
        if (scmConfigId == null || !hasText(branchName)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<CodeRepositoryIndex>()
                .eq(CodeRepositoryIndex::getScmConfigId, scmConfigId)
                .eq(CodeRepositoryIndex::getBranchName, branchName)
                .eq(CodeRepositoryIndex::getScanStatus, "SUCCESS")
                .eq(CodeRepositoryIndex::getStale, false)
                .eq(CodeRepositoryIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(CodeRepositoryIndex::getFinishedAt)
                .orderByDesc(CodeRepositoryIndex::getId)
                .last("limit 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

