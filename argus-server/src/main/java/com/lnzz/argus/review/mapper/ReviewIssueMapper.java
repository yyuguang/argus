package com.lnzz.argus.review.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.review.entity.ReviewIssue;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 评审问题 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ReviewIssueMapper extends BaseMapper<ReviewIssue> {

    /**
     * 查询评审任务的问题列表。
     *
     * @param taskId 评审任务 ID
     * @return 问题列表
     */
    default List<ReviewIssue> findByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapper<ReviewIssue>()
                .eq(ReviewIssue::getTaskId, taskId)
                .orderByAsc(ReviewIssue::getSeverity)
                .orderByAsc(ReviewIssue::getFilePath)
                .orderByAsc(ReviewIssue::getStartLine));
    }
}
