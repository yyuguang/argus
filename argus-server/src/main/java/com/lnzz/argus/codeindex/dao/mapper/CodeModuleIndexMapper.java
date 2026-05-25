package com.lnzz.argus.codeindex.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodeModuleIndex;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @classname: CodeModuleIndexMapper
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: 模块源码索引 Mapper，封装指定索引快照下的模块查询语义。
 */
@Mapper
public interface CodeModuleIndexMapper extends BaseMapper<CodeModuleIndex> {

    /**
     * 查询指定索引快照下的模块列表。
     *
     * @param indexId 仓库源码索引 ID
     * @return 模块索引列表
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default List<CodeModuleIndex> selectByIndexId(Long indexId) {
        if (indexId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<CodeModuleIndex>()
                .eq(CodeModuleIndex::getIndexId, indexId)
                .eq(CodeModuleIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(CodeModuleIndex::getModulePath)
                .orderByAsc(CodeModuleIndex::getId));
    }

    @Delete("delete from argus_code_module_index where index_id = #{indexId}")
    int deletePhysicalByIndexId(@Param("indexId") Long indexId);

    @Insert({
            "<script>",
            "insert into argus_code_module_index (",
            "index_id, scm_config_id, module_name, module_path, parent_module_path, build_type, packaging,",
            "source_roots, java_file_count, class_count, scan_status, warning_message, is_deleted, version",
            ") values",
            "<foreach collection='records' item='item' separator=','>",
            "(",
            "#{item.indexId}, #{item.scmConfigId}, #{item.moduleName}, #{item.modulePath},",
            "#{item.parentModulePath}, #{item.buildType}, #{item.packaging}, #{item.sourceRoots},",
            "#{item.javaFileCount}, #{item.classCount}, #{item.scanStatus}, #{item.warningMessage},",
            "#{item.isDeleted}, #{item.version}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("records") List<CodeModuleIndex> records);
}
