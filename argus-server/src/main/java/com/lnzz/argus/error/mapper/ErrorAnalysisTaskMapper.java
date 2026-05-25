package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorAnalysisTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 错误分析任务 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorAnalysisTaskMapper extends BaseMapper<ErrorAnalysisTask> {

    /**
     * 查询错误事件分析任务。
     *
     * @param eventId 错误事件 ID
     * @return 分析任务列表
     */
    default List<ErrorAnalysisTask> findByEventId(Long eventId) {
        return selectList(new LambdaQueryWrapper<ErrorAnalysisTask>()
                .eq(ErrorAnalysisTask::getErrorEventId, eventId)
                .orderByDesc(ErrorAnalysisTask::getCreateTime)
                .orderByDesc(ErrorAnalysisTask::getId));
    }
}
