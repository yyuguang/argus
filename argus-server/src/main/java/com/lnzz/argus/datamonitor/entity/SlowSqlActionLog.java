package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 慢 SQL 人工处理日志。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_slow_sql_action_log")
public class SlowSqlActionLog extends BaseEntity {

    private Long slowSqlEventId;
    private String actionType;
    private String operator;
    private String reason;
    private String beforeStatus;
    private String afterStatus;
    private String detailJson;
}
