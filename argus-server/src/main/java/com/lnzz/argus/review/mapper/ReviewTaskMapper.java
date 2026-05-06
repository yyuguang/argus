package com.lnzz.argus.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.review.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评审任务 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {
}
