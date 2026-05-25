package com.lnzz.argus.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评审任务查询服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskQueryService {

    private final ReviewTaskMapper reviewTaskMapper;

    public Page<ReviewTask> queryTasks(long pageNo, long pageSize, String scmProvider, String status, String keyword) {
        log.debug("查询评审任务: pageNo={}, pageSize={}, scmProvider={}, status={}, keyword={}",
                pageNo, pageSize, scmProvider, status, keyword);
        return reviewTaskMapper.queryTasks(pageNo, pageSize, scmProvider, status, keyword);
    }
}
