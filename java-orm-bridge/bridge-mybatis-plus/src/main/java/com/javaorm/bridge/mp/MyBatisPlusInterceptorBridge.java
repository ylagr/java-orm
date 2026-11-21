package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.interceptor.SqlInterceptor;
import com.javaorm.core.metadata.EntityMetadata;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis-Plus 插件生态桥接器
 * 将 MyBatis-Plus 的 MybatisPlusInterceptor 或各类 InnerInterceptor（如多租户 TenantLineInnerInterceptor、
 * 动态表名 DynamicTableNameInnerInterceptor、防全表更新 BlockAttackInnerInterceptor、数据权限 DataPermissionInterceptor、
 * 非法 SQL 拦截 IllegalSQLInnerInterceptor 等）无缝桥接为 JavaORM 的 SqlInterceptor。
 */
public class MyBatisPlusInterceptorBridge implements SqlInterceptor {

    private final List<InnerInterceptor> innerInterceptors = new ArrayList<>();
    private int order = 0;

    private static final Method CHANGE_TABLE_METHOD;

    static {
        Method method = null;
        try {
            method = DynamicTableNameInnerInterceptor.class.getDeclaredMethod("changeTable", String.class);
            method.setAccessible(true);
        } catch (Throwable ignored) {
        }
        CHANGE_TABLE_METHOD = method;
    }

    public MyBatisPlusInterceptorBridge() {
    }

    public MyBatisPlusInterceptorBridge(MybatisPlusInterceptor mybatisPlusInterceptor) {
        if (mybatisPlusInterceptor != null && mybatisPlusInterceptor.getInterceptors() != null) {
            this.innerInterceptors.addAll(mybatisPlusInterceptor.getInterceptors());
        }
    }

    public MyBatisPlusInterceptorBridge(InnerInterceptor innerInterceptor) {
        if (innerInterceptor != null) {
            this.innerInterceptors.add(innerInterceptor);
        }
    }

    public MyBatisPlusInterceptorBridge(List<InnerInterceptor> innerInterceptors) {
        if (innerInterceptors != null) {
            this.innerInterceptors.addAll(innerInterceptors);
        }
    }

    public MyBatisPlusInterceptorBridge addInnerInterceptor(InnerInterceptor innerInterceptor) {
        if (innerInterceptor != null) {
            this.innerInterceptors.add(innerInterceptor);
        }
        return this;
    }

    public List<InnerInterceptor> getInnerInterceptors() {
        return Collections.unmodifiableList(innerInterceptors);
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public SqlAndParams beforeQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        if (sqlAndParams == null || sqlAndParams.getSql() == null || innerInterceptors.isEmpty()) {
            return sqlAndParams;
        }

        String currentSql = sqlAndParams.getSql();
        for (InnerInterceptor interceptor : innerInterceptors) {
            if (interceptor instanceof DynamicTableNameInnerInterceptor) {
                currentSql = processDynamicTableName((DynamicTableNameInnerInterceptor) interceptor, currentSql);
            } else if (interceptor instanceof JsqlParserSupport) {
                try {
                    currentSql = ((JsqlParserSupport) interceptor).parserSingle(currentSql, null);
                } catch (UnsupportedOperationException ignored) {
                    // MP 拦截器未重写该 SQL 类型处理器时安全跳过
                }
            }
        }

        if (currentSql.equals(sqlAndParams.getSql())) {
            return sqlAndParams;
        }
        return new SqlAndParams(currentSql, sqlAndParams.getParams());
    }

    @Override
    public SqlAndParams beforeUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        if (sqlAndParams == null || sqlAndParams.getSql() == null || innerInterceptors.isEmpty()) {
            return sqlAndParams;
        }

        String currentSql = sqlAndParams.getSql();
        for (InnerInterceptor interceptor : innerInterceptors) {
            if (interceptor instanceof DynamicTableNameInnerInterceptor) {
                currentSql = processDynamicTableName((DynamicTableNameInnerInterceptor) interceptor, currentSql);
            } else if (interceptor instanceof JsqlParserSupport) {
                try {
                    currentSql = ((JsqlParserSupport) interceptor).parserSingle(currentSql, null);
                } catch (UnsupportedOperationException ignored) {
                    // MP 拦截器（如 BlockAttackInnerInterceptor）未重写 INSERT 处理器时安全跳过
                }
            }
        }

        if (currentSql.equals(sqlAndParams.getSql())) {
            return sqlAndParams;
        }
        return new SqlAndParams(currentSql, sqlAndParams.getParams());
    }

    private String processDynamicTableName(DynamicTableNameInnerInterceptor interceptor, String sql) {
        if (CHANGE_TABLE_METHOD != null) {
            try {
                return (String) CHANGE_TABLE_METHOD.invoke(interceptor, sql);
            } catch (Exception e) {
                Throwable target = e.getCause() != null ? e.getCause() : e;
                if (target instanceof RuntimeException) {
                    throw (RuntimeException) target;
                }
                throw new RuntimeException("Failed to process dynamic table name", target);
            }
        }
        return sql;
    }
}
