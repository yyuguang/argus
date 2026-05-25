package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ProjectMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用-SCM项目映射 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ProjectMappingMapper extends BaseMapper<ProjectMapping> {

    /**
     * 按主键查询应用映射。
     *
     * @param id 应用映射 ID
     * @return 应用映射
     */
    default ProjectMapping findById(Long id) {
        return selectById(id);
    }
}
