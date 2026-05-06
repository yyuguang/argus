package com.lnzz.argus.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.notification.entity.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知记录 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {
}
