package com.lnzz.argus.codeindex.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodeIndexScanTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * @classname: CodeIndexScanTaskMapper
 * @author: Fantasy
 * @date: 2026/05/25 08:35
 * @description: 源码索引扫描任务 Mapper，提供扫描任务持久化访问能力。
 */
@Mapper
public interface CodeIndexScanTaskMapper extends BaseMapper<CodeIndexScanTask> {
}
