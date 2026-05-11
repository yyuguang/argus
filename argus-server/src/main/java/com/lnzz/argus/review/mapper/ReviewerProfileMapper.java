package com.lnzz.argus.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.review.entity.ReviewerProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 提交者画像 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ReviewerProfileMapper extends BaseMapper<ReviewerProfile> {

    /**
     * 按作者和平台查询画像。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 画像
     */
    @Select("SELECT * FROM argus_reviewer_profile WHERE author_name = #{authorName} AND scm_provider = #{scmProvider} LIMIT 1")
    ReviewerProfile selectByAuthorNameAndProvider(@Param("authorName") String authorName,
                                                  @Param("scmProvider") String scmProvider);

    /**
     * 按作者唯一ID查询画像。
     *
     * @param authorId 作者唯一ID
     * @return 画像
     */
    @Select("SELECT * FROM argus_reviewer_profile WHERE author_id = #{authorId} LIMIT 1")
    ReviewerProfile selectByAuthorId(@Param("authorId") String authorId);

    /**
     * 查询团队画像列表，按平均分降序。
     *
     * @param scmProvider SCM 平台
     * @param limit 返回条数
     * @return 画像列表
     */
    @Select("SELECT * FROM argus_reviewer_profile WHERE scm_provider = #{scmProvider} "
            + "ORDER BY avg_score DESC, total_reviews DESC LIMIT #{limit}")
    List<ReviewerProfile> selectTopByAvgScore(@Param("scmProvider") String scmProvider,
                                              @Param("limit") int limit);

    /**
     * 查询指定平台的全部画像。
     *
     * @param scmProvider SCM 平台
     * @return 画像列表
     */
    @Select("SELECT * FROM argus_reviewer_profile WHERE scm_provider = #{scmProvider}")
    List<ReviewerProfile> selectByScmProvider(@Param("scmProvider") String scmProvider);
}
