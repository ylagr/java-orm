package com.javaorm.core.query;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;

import javax.sql.DataSource;

/**
 * 自定义字段投影查询的第一阶段（等待 .from(Entity.class) 绑定数据源实体）。
 */
public class FromStageQuery {

    private final Object[] expressions;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public FromStageQuery(Object[] expressions, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.expressions = expressions;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
    }

    public <T> Query<T> from(Class<T> entityClass) {
        String existingAlias = hasSubQuery() ? com.javaorm.core.proxy.RecordingContext.getAlias(entityClass) : null;
        Query<T> query = new Query<>(entityClass, dataSource, dialect, config, existingAlias);
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
