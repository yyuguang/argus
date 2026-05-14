package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 错误分析结果 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorAnalysisMapper extends BaseMapper<ErrorAnalysis> {

    /**
     * M5-B02: 查询同指纹的历史分析案例（最近 N 条）
     */
    @Select("""
            SELECT ea.* FROM argus_error_analysis ea
            JOIN argus_error_event ee ON ea.error_event_id = ee.id
            WHERE ee.error_fingerprint = #{fingerprint}
              AND ee.id != #{excludeEventId}
            ORDER BY ea.create_time DESC
            LIMIT #{limit}
            """)
    List<ErrorAnalysis> findHistoryByFingerprint(@Param("fingerprint") String fingerprint,
                                                   @Param("excludeEventId") Long excludeEventId,
                                                   @Param("limit") int limit);

    /**
     * 查询同类型错误的历史分析案例
     */
    @Select("""
            SELECT ea.* FROM argus_error_analysis ea
            JOIN argus_error_event ee ON ea.error_event_id = ee.id
            WHERE ee.error_type = #{errorType}
              AND ee.app_name = #{appName}
              AND ee.id != #{excludeEventId}
            ORDER BY ea.create_time DESC
            LIMIT #{limit}
            """)
    List<ErrorAnalysis> findHistoryByErrorType(@Param("errorType") String errorType,
                                                 @Param("appName") String appName,
                                                 @Param("excludeEventId") Long excludeEventId,
                                                 @Param("limit") int limit);
}
