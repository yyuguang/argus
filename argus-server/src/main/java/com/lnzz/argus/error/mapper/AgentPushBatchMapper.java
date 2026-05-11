package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.AgentPushBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 推送批次记录 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface AgentPushBatchMapper extends BaseMapper<AgentPushBatch> {
}
