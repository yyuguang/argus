package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.LogQualityCheckResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志质量巡检结果 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface LogQualityCheckResultMapper extends BaseMapper<LogQualityCheckResult> {
}
