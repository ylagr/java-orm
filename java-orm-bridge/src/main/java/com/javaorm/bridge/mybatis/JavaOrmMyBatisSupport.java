package com.javaorm.bridge.mybatis;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.sql.DataSource;

/**
 * MyBatis 与 JavaORM 集成辅助工具类
 */
public class JavaOrmMyBatisSupport {

    private JavaOrmMyBatisSupport() {
    }

    /**
     * 根据 MyBatis SqlSession 构建专属的 JavaOrm 实例
     * 自动从 MyBatis Configuration 中提取 DataSource，并将执行器绑定至当前 SqlSession。
     */
    public static JavaOrm from(SqlSession sqlSession) {
        if (sqlSession == null) {
            throw new IllegalArgumentException("SqlSession cannot be null");
        }
        Configuration configuration = sqlSession.getConfiguration();
        DataSource dataSource = resolveDataSource(configuration);

        OrmConfig config = new OrmConfig();
        config.setSqlExecutor(new MyBatisSqlExecutor(sqlSession));
        autoRegisterInterceptors(config, configuration);

        return JavaOrm.create(dataSource, config);
    }

    /**
     * 根据 MyBatis SqlSessionFactory 构建 JavaOrm 实例
     */
    public static JavaOrm from(SqlSessionFactory sqlSessionFactory) {
        if (sqlSessionFactory == null) {
            throw new IllegalArgumentException("SqlSessionFactory cannot be null");
        }
        Configuration configuration = sqlSessionFactory.getConfiguration();
        DataSource dataSource = resolveDataSource(configuration);

        OrmConfig config = new OrmConfig();
        config.setSqlExecutor(new MyBatisSqlExecutor(sqlSessionFactory));
        autoRegisterInterceptors(config, configuration);

        return JavaOrm.create(dataSource, config);
    }

    public static void autoRegisterInterceptors(OrmConfig config, Configuration configuration) {
        if (configuration == null || configuration.getInterceptors() == null) {
            return;
        }
        for (org.apache.ibatis.plugin.Interceptor interceptor : configuration.getInterceptors()) {
            if (interceptor instanceof com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor) {
                config.addInterceptor(new com.javaorm.bridge.mp.MyBatisPlusInterceptorBridge(
                        (com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor) interceptor));
            } else if (interceptor instanceof com.javaorm.core.interceptor.SqlInterceptor) {
                config.addInterceptor((com.javaorm.core.interceptor.SqlInterceptor) interceptor);
            }
        }
    }

    private static DataSource resolveDataSource(Configuration configuration) {
        if (configuration != null) {
            Environment environment = configuration.getEnvironment();
            if (environment != null) {
                return environment.getDataSource();
            }
        }
        return null;
    }
}
