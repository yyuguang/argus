package com.lnzz.argus.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * @author lnzz
 * @since 1.0.0
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 插件链。
     *
     * <p>系统用户、角色、菜单等实体使用 {@code @Version} 字段进行乐观锁控制。
     * {@link OptimisticLockerInnerInterceptor} 必须注册到插件链中，才能在执行更新 SQL 前
     * 为 MyBatis-Plus 生成的 {@code MP_OPTLOCK_VERSION_ORIGINAL} 参数赋值。</p>
     *
     * <p>分页插件保留在最后，避免后续新增查询类插件时影响分页 SQL 的最终改写结果。</p>
     *
     * @return MyBatis-Plus 全局拦截器配置
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
