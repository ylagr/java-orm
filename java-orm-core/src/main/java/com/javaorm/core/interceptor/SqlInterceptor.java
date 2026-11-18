package com.javaorm.core.interceptor;

import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.EntityMetadata;

import java.util.List;

/**
 * SQL 拦截器/切面契约（支持查询与更新的前置改写、审计记录与后置耗时统计）。
 */
public interface SqlInterceptor {

    /**
     * 拦截器排序（数值越小越先执行）
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 查询执行前拦截（支持改写 SQL 和参数，如数据权限、租户条件增强）
     */
    default SqlAndParams beforeQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        return sqlAndParams;
    }

    /**
     * 更新/删除/插入执行前拦截（支持改写 SQL 和参数）
     */
    default SqlAndParams beforeUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        return sqlAndParams;
    }

    /**
     * SQL 执行后拦截（耗时统计、慢 SQL 日志、异常捕获等）
     */
    default void afterExecution(String sql, List<Object> params, long elapsedMillis, Throwable throwable) {
    }
}
