package com.lnzz.argus.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("MyBatisPlusConfig - 插件配置")
class MyBatisPlusConfigTest {

    @Test
    @DisplayName("@Version 实体更新需要启用乐观锁插件")
    void mybatisPlusInterceptorContainsOptimisticLockerBeforePagination() {
        MybatisPlusInterceptor interceptor = new MyBatisPlusConfig().mybatisPlusInterceptor();

        assertEquals(2, interceptor.getInterceptors().size());
        assertInstanceOf(OptimisticLockerInnerInterceptor.class, interceptor.getInterceptors().get(0));
        assertInstanceOf(PaginationInnerInterceptor.class, interceptor.getInterceptors().get(1));
    }
}
