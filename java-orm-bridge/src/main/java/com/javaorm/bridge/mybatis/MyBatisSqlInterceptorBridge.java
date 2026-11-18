package com.javaorm.bridge.mybatis;

import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.interceptor.SqlInterceptor;
import com.javaorm.core.metadata.EntityMetadata;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;
import java.util.Properties;

/**
 * MyBatis 拦截器与切面桥接器。
 * 1. 可作为 JavaORM 原生 SqlInterceptor，直接挂载到 OrmConfig 中；
 * 2. 也可作为 MyBatis 标准 Plugin 插件注册到 MyBatis Configuration 中。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class MyBatisSqlInterceptorBridge implements SqlInterceptor, Interceptor {

    private final Interceptor mybatisInterceptor;
    private final com.javaorm.bridge.mp.MyBatisPlusInterceptorBridge mpBridge;

    public MyBatisSqlInterceptorBridge() {
        this.mybatisInterceptor = null;
        this.mpBridge = null;
    }

    public MyBatisSqlInterceptorBridge(Interceptor mybatisInterceptor) {
        this.mybatisInterceptor = mybatisInterceptor;
        if (mybatisInterceptor instanceof com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor) {
            this.mpBridge = new com.javaorm.bridge.mp.MyBatisPlusInterceptorBridge(
                    (com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor) mybatisInterceptor);
        } else {
            this.mpBridge = null;
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public SqlAndParams beforeQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        if (mpBridge != null) {
            return mpBridge.beforeQuery(sqlAndParams, metadata, dialect);
        }
        return sqlAndParams;
    }

    @Override
    public SqlAndParams beforeUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        if (mpBridge != null) {
            return mpBridge.beforeUpdate(sqlAndParams, metadata, dialect);
        }
        return sqlAndParams;
    }

    @Override
    public void afterExecution(String sql, List<Object> params, long elapsedMillis, Throwable throwable) {
        if (mpBridge != null) {
            mpBridge.afterExecution(sql, params, elapsedMillis, throwable);
        }
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (mybatisInterceptor != null) {
            return mybatisInterceptor.intercept(invocation);
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        if (mybatisInterceptor != null) {
            mybatisInterceptor.setProperties(properties);
        }
    }
}
