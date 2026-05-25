package com.lnzz.argus.codeindex.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodeClassIndex;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @classname: CodeClassIndexMapper
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: Java 类型源码索引 Mapper，封装 FQN、文件路径和简单类名定位查询。
 */
@Mapper
public interface CodeClassIndexMapper extends BaseMapper<CodeClassIndex> {

    /**
     * 按全限定名查询指定索引快照下的类型索引。
     *
     * @param indexId       仓库源码索引 ID
     * @param qualifiedName Java 全限定名
     * @return 类型索引候选列表
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default List<CodeClassIndex> selectByQualifiedName(Long indexId, String qualifiedName) {
        if (indexId == null || !hasText(qualifiedName)) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<CodeClassIndex>()
                .eq(CodeClassIndex::getIndexId, indexId)
                .eq(CodeClassIndex::getQualifiedName, qualifiedName)
                .eq(CodeClassIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(CodeClassIndex::getPrimaryType)
                .orderByAsc(CodeClassIndex::getFilePath));
    }

    /**
     * 按文件路径查询指定索引快照下的类型索引。
     *
     * @param indexId  仓库源码索引 ID
     * @param filePath SCM 仓库相对文件路径
     * @return 类型索引候选列表
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default List<CodeClassIndex> selectByFilePath(Long indexId, String filePath) {
        if (indexId == null || !hasText(filePath)) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<CodeClassIndex>()
                .eq(CodeClassIndex::getIndexId, indexId)
                .eq(CodeClassIndex::getFilePath, filePath)
                .eq(CodeClassIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(CodeClassIndex::getPrimaryType)
                .orderByAsc(CodeClassIndex::getId));
    }

    /**
     * 按简单类名查询指定索引快照下的类型索引候选。
     *
     * @param indexId   仓库源码索引 ID
     * @param className 简单类型名
     * @return 类型索引候选列表
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default List<CodeClassIndex> selectByClassName(Long indexId, String className) {
        if (indexId == null || !hasText(className)) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<CodeClassIndex>()
                .eq(CodeClassIndex::getIndexId, indexId)
                .eq(CodeClassIndex::getClassName, className)
                .eq(CodeClassIndex::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(CodeClassIndex::getQualifiedName)
                .orderByAsc(CodeClassIndex::getFilePath));
    }

    @Delete("delete from argus_code_class_index where index_id = #{indexId}")
    int deletePhysicalByIndexId(@Param("indexId") Long indexId);

    @Insert({
            "<script>",
            "insert into argus_code_class_index (",
            "index_id, scm_config_id, module_path, source_root, file_path, file_sha, package_name, class_name,",
            "qualified_name, class_kind, primary_type, line_start, line_end, imports_json, parser_status,",
            "confidence, is_deleted, version",
            ") values",
            "<foreach collection='records' item='item' separator=','>",
            "(",
            "#{item.indexId}, #{item.scmConfigId}, #{item.modulePath}, #{item.sourceRoot}, #{item.filePath},",
            "#{item.fileSha}, #{item.packageName}, #{item.className}, #{item.qualifiedName}, #{item.classKind},",
            "#{item.primaryType}, #{item.lineStart}, #{item.lineEnd}, #{item.importsJson},",
            "#{item.parserStatus}, #{item.confidence}, #{item.isDeleted}, #{item.version}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("records") List<CodeClassIndex> records);

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
