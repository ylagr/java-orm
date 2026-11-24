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
        autoRegisterTypeHandlers(config, configuration);

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
        autoRegisterTypeHandlers(config, configuration);

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

    public static void autoRegisterTypeHandlers(OrmConfig config, Configuration configuration) {
        if (config == null || configuration == null) return;
        org.apache.ibatis.type.TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        if (registry == null) return;

        java.util.Set<Class<?>> processedTypes = new java.util.HashSet<>();

        // 1. 尝试通过反射读取 typeHandlerMap 映射，获取所有显式注册了 TypeHandler 的自定义类 / 枚举类
        try {
            java.lang.reflect.Field mapField = org.apache.ibatis.type.TypeHandlerRegistry.class.getDeclaredField("typeHandlerMap");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.lang.reflect.Type, ?> map = (java.util.Map<java.lang.reflect.Type, ?>) mapField.get(registry);
            if (map != null) {
                for (java.lang.reflect.Type t : map.keySet()) {
                    if (t instanceof Class<?>) {
                        Class<?> clazz = (Class<?>) t;
                        if (!isJdkBasicType(clazz)) {
                            org.apache.ibatis.type.TypeHandler<?> handler = registry.getTypeHandler(clazz);
                            if (handler != null && !(handler instanceof org.apache.ibatis.type.UnknownTypeHandler)) {
                                config.addAutoConverter(new MyBatisAutoTypeHandlerConverter<>(handler, clazz));
                                processedTypes.add(clazz);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. 备用兜底：遍历 getTypeHandlers()，根据 TypeReference 获取绑定的泛型 rawType
        try {
            java.util.Collection<org.apache.ibatis.type.TypeHandler<?>> handlers = registry.getTypeHandlers();
            if (handlers != null) {
                for (org.apache.ibatis.type.TypeHandler<?> handler : handlers) {
                    if (handler instanceof org.apache.ibatis.type.TypeReference<?>) {
                        java.lang.reflect.Type raw = ((org.apache.ibatis.type.TypeReference<?>) handler).getRawType();
                        if (raw instanceof Class<?>) {
                            Class<?> clazz = (Class<?>) raw;
                            if (!isJdkBasicType(clazz) && processedTypes.add(clazz)) {
                                config.addAutoConverter(new MyBatisAutoTypeHandlerConverter<>(handler, clazz));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isJdkBasicType(Class<?> clazz) {
        if (clazz == null || clazz.isPrimitive()) return true;
        String name = clazz.getName();
        if (name.startsWith("java.lang.") || name.startsWith("java.math.") || name.startsWith("java.sql.")) {
            return true;
        }
        if (name.startsWith("java.time.") || name.startsWith("java.io.")) {
            return true;
        }
        if (clazz.isArray() && (clazz.getComponentType().isPrimitive() || clazz.getComponentType().getName().startsWith("java.lang."))) {
            return true;
        }
        return clazz == java.util.Date.class;
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
