package com.lnzz.argus.notification.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.notification.entity.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 通知记录 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {

    /**
     * 查询指定业务引用的通知记录。
     *
     * @param refType 引用类型
     * @param refId   引用 ID
     * @param limit   最大返回数量
     * @return 通知记录列表
     */
    default List<NotificationRecord> findByRef(String refType, Long refId, int limit) {
        return selectList(new LambdaQueryWrapper<NotificationRecord>()
                .eq(NotificationRecord::getRefType, refType)
                .eq(NotificationRecord::getRefId, refId)
                .orderByDesc(NotificationRecord::getCreateTime)
                .last("LIMIT " + limit));
    }
}
