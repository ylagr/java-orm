package com.javaorm.bridge.mybatis;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 原生 MyBatis 与 JavaORM 的桥接支持工具类。
 * 1. 从已有的 SqlSession 或 SqlSessionFactory 构造 JavaOrm 实例；
 * 2. 自动共享底层 DataSource 和事务；
 * 3. 自动扫描并集成 MyBatis 配置中的 Interceptors；
 * 4. 若环境中存在 MyBatis-Plus，通过反射无损自动激活 MP 插件与 MetaObjectHandler。
 */
public class JavaOrmMyBatisSupport {

    private JavaOrmMyBatisSupport() {
    }

    public static JavaOrm from(SqlSession sqlSession) {
        if (sqlSession == null) {
            throw new IllegalArgumentException("SqlSession must not be null");
        }
        Configuration configuration = sqlSession.getConfiguration();
        DataSource dataSource = resolveDataSource(configuration);

        OrmConfig config = new OrmConfig();
        config.setSqlExecutor(new MyBatisSqlExecutor(sqlSession));

        autoRegisterInterceptors(config, configuration);
        autoRegisterMetaObjectHandler(config, configuration);

        return JavaOrm.create(dataSource, config);
    }

    public static JavaOrm from(SqlSessionFactory sqlSessionFactory) {
        if (sqlSessionFactory == null) {
            throw new IllegalArgumentException("SqlSessionFactory must not be null");
        }
        Configuration configuration = sqlSessionFactory.getConfiguration();
        DataSource dataSource = resolveDataSource(configuration);

        OrmConfig config = new OrmConfig();
        config.setSqlExecutor(new MyBatisSqlExecutor(sqlSessionFactory));

        autoRegisterInterceptors(config, configuration);
        autoRegisterMetaObjectHandler(config, configuration);

        return JavaOrm.create(dataSource, config);
    }

    public static void autoRegisterMetaObjectHandler(OrmConfig config, Configuration configuration) {
        if (config == null || configuration == null) return;
        try {
            Class<?> utilsClass = Class.forName("com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils");
            Method method = utilsClass.getMethod("getMetaObjectHandler", Configuration.class);
            Object result = method.invoke(null, configuration);
            if (result instanceof Optional) {
                Optional<?> opt = (Optional<?>) result;
                if (opt.isPresent()) {
                    Object handler = opt.get();
                    Class<?> bridgeClass = Class.forName("com.javaorm.bridge.mp.MyBatisPlusMetaObjectHandlerBridge");
                    Class<?> handlerClass = Class.forName("com.baomidou.mybatisplus.core.handlers.MetaObjectHandler");
                    Constructor<?> ctor = bridgeClass.getConstructor(handlerClass);
                    com.javaorm.core.handler.AutoFillHandler bridge = (com.javaorm.core.handler.AutoFillHandler) ctor.newInstance(handler);
                    config.autoFillHandler(bridge);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void autoRegisterInterceptors(OrmConfig config, Configuration configuration) {
        if (configuration == null || configuration.getInterceptors() == null) {
            return;
        }
        for (org.apache.ibatis.plugin.Interceptor interceptor : configuration.getInterceptors()) {
            if (interceptor instanceof com.javaorm.core.interceptor.SqlInterceptor) {
                config.addInterceptor((com.javaorm.core.interceptor.SqlInterceptor) interceptor);
            } else {
                try {
                    Class<?> mpInterceptorClass = Class.forName("com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor");
                    if (mpInterceptorClass.isInstance(interceptor)) {
                        Class<?> bridgeClass = Class.forName("com.javaorm.bridge.mp.MyBatisPlusInterceptorBridge");
                        Constructor<?> ctor = bridgeClass.getConstructor(mpInterceptorClass);
                        com.javaorm.core.interceptor.SqlInterceptor bridge = (com.javaorm.core.interceptor.SqlInterceptor) ctor.newInstance(interceptor);
                        config.addInterceptor(bridge);
                    }
                } catch (Throwable ignored) {
                }
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
        throw new IllegalStateException("Cannot resolve DataSource from MyBatis Configuration Environment");
    }
}
