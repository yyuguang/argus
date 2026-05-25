package com.lnzz.argus.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lnzz.argus.security.LoginUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * <p>自动填充 createBy/createTime/updateBy/updateTime</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String operator = LoginUtil.currentUsernameOrSystem();
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", String.class, operator);
        this.strictInsertFill(metaObject, "updateBy", String.class, operator);
        if (log.isTraceEnabled()) {
            log.trace("MyBatis 自动填充创建审计字段: operator={}, entity={}",
                    operator, entityName(metaObject));
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String operator = LoginUtil.currentUsernameOrSystem();
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updateBy", String.class, operator);
        if (log.isTraceEnabled()) {
            log.trace("MyBatis 自动填充更新审计字段: operator={}, entity={}",
                    operator, entityName(metaObject));
        }
    }

    private String entityName(MetaObject metaObject) {
        Object source = metaObject == null ? null : metaObject.getOriginalObject();
        return source == null ? "unknown" : source.getClass().getSimpleName();
    }
}
