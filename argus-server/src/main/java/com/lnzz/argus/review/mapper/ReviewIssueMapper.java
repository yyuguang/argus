package com.lnzz.argus.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.review.entity.ReviewIssue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评审问题 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ReviewIssueMapper extends BaseMapper<ReviewIssue> {
}
