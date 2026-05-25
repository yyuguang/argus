package com.lnzz.argus.codeindex.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodePackageIndex;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @classname: CodePackageIndexMapper
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: Java 包源码索引 Mapper，封装 package 到模块分布的查询语义。
 */
@Mapper
public interface CodePackageIndexMapper extends BaseMapper<CodePackageIndex> {

    /**
     * 查询指定索引快照下的歧义 package 列表。
     *
     * @param indexId 仓库源码索引 ID
     * @return 歧义 package 索引列表
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default List<CodePackageIndex> selectAmbiguousPackages(Long indexId) {
        if (indexId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<CodePackageIndex>()
                .eq(CodePackageIndex::getIndexId, indexId)
                .eq(CodePackageIndex::getAmbiguous, true)
                .eq(CodePackageIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(CodePackageIndex::getPackageName));
    }

    /**
     * 按 package 名称查询指定索引快照下的模块分布。
     *
     * @param indexId     仓库源码索引 ID
     * @param packageName Java package 名称
     * @return package 索引；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default CodePackageIndex selectByPackageName(Long indexId, String packageName) {
        if (indexId == null || !hasText(packageName)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<CodePackageIndex>()
                .eq(CodePackageIndex::getIndexId, indexId)
                .eq(CodePackageIndex::getPackageName, packageName)
                .eq(CodePackageIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    @Delete("delete from argus_code_package_index where index_id = #{indexId}")
    int deletePhysicalByIndexId(@Param("indexId") Long indexId);

    @Insert({
            "<script>",
            "insert into argus_code_package_index (",
            "index_id, scm_config_id, package_name, module_paths, primary_module_path, class_count, ambiguous,",
            "confidence, is_deleted, version",
            ") values",
            "<foreach collection='records' item='item' separator=','>",
            "(",
            "#{item.indexId}, #{item.scmConfigId}, #{item.packageName}, #{item.modulePaths},",
            "#{item.primaryModulePath}, #{item.classCount}, #{item.ambiguous}, #{item.confidence},",
            "#{item.isDeleted}, #{item.version}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("records") List<CodePackageIndex> records);

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
