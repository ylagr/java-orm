package com.javaorm.core.interceptor;

import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.EntityMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQL 拦截器调用链管理中心
 */
public class InterceptorChain {

    private final List<SqlInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public void addInterceptor(SqlInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
            interceptors.sort(Comparator.comparingInt(SqlInterceptor::getOrder));
        }
    }

    public List<SqlInterceptor> getInterceptors() {
        return interceptors;
    }

    public boolean isEmpty() {
        return interceptors.isEmpty();
    }

    public SqlAndParams pluginQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        SqlAndParams current = sqlAndParams;
        for (SqlInterceptor interceptor : interceptors) {
            current = interceptor.beforeQuery(current, metadata, dialect);
        }
        return current;
    }

    public SqlAndParams pluginUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        SqlAndParams current = sqlAndParams;
        for (SqlInterceptor interceptor : interceptors) {
            current = interceptor.beforeUpdate(current, metadata, dialect);
        }
        return current;
    }

    public void triggerAfterExecution(String sql, List<Object> params, long elapsedMillis, Throwable throwable) {
        for (SqlInterceptor interceptor : interceptors) {
            try {
                interceptor.afterExecution(sql, params, elapsedMillis, throwable);
            } catch (Throwable ignored) {
            }
        }
    }
}
