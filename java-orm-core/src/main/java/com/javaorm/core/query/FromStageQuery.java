package com.javaorm.core.query;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;

import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;

/**
 * 自定义字段投影查询的第一阶段（等待 .from(Entity.class) 绑定数据源实体）。
 */
public class FromStageQuery {

    private final SqlEnv env;
    private final Object[] expressions;

    public FromStageQuery(SqlEnv env, Object[] expressions) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.expressions = expressions;
    }

    public FromStageQuery(Object[] expressions, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), expressions);
    }

    public SqlEnv getEnv() {
        return env;
    }

    public DataSource getDataSource() {
        return env.getDataSource();
    }

    public Dialect getDialect() {
        return env.getDialect();
    }

    public OrmConfig getConfig() {
        return env.getConfig();
    }

    public <T> Query<T> from(Class<T> entityClass) {
        String existingAlias = hasSubQuery() ? com.javaorm.core.proxy.RecordingContext.getAlias(entityClass) : null;
        Query<T> query = new Query<>(env, entityClass, existingAlias);
        query.select(expressions);
        return query;
    }

    public <T> Query<T> from(Cte<T> cte) {
        Query<T> query = new Query<>(env, cte);
        query.select(expressions);
        return query;
    }


    private boolean hasSubQuery() {
        if (expressions == null) return false;
        for (Object expr : expressions) {
            if (expr instanceof com.javaorm.core.ast.SubQueryExpression) {
                return true;
            }
        }
        return false;
    }
}
