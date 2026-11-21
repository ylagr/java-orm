package com.javaorm.sample.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置类
 * <p>
 * 声明的 MP 拦截器会被 MyBatisPlusAdapter 自动探测并挂载到 JavaORM 中，
 * 在使用 JavaORM 执行更新/删除时，同样生效防全表攻击与乐观锁控制！
 */
@Configuration
public class MyBatisPlusMetaConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 注册乐观锁拦截器
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 2. 注册防全表更新/删除恶意攻击插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
