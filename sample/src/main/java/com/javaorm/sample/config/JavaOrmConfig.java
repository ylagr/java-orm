package com.javaorm.sample.config;

import com.javaorm.bridge.mp.MyBatisPlusAdapter;
import com.javaorm.core.JavaOrm;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * JavaORM 核心配置类（MyBatis-Plus 兼容模式适配入口）
 * <p>
 * 核心原理：
 * 通过 {@link MyBatisPlusAdapter#create(SqlSessionFactory)} 将 MyBatis-Plus 的
 * 全家桶（MP 实体注解、MyBatis 事务生命周期、防全表攻击插件、乐观锁拦截器等）完整适配至 JavaOrm，
 * 产生一个 Spring 托管的全局 JavaOrm 单例 Bean，供业务 Service 注入使用。
 */
@Configuration
public class JavaOrmConfig {

    @Bean
    public JavaOrm javaOrm(SqlSessionFactory sqlSessionFactory) {
        // 一键完成 MyBatis-Plus 全家桶适配，后续业务开发直接注入 JavaOrm 即可！
        return MyBatisPlusAdapter.create(sqlSessionFactory);
    }
}
