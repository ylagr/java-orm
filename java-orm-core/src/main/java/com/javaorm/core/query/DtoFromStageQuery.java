package com.javaorm.core.query;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;

import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;

/**
 * 带有 DTO 类和投影字段的第一阶段（等待 .from(Entity.class) 绑定数据源实体）。
 */
public class DtoFromStageQuery<DTO> {

    private final SqlEnv env;
    private final Class<DTO> dtoClass;
    private final Object[] expressions;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public DtoFromStageQuery(SqlEnv env, Class<DTO> dtoClass, Object[] expressions) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.dtoClass = dtoClass;
        this.expressions = expressions;
        this.dataSource = this.env.getDataSource();
        this.dialect = this.env.getDialect();
        this.config = this.env.getConfig();
    }

    public DtoFromStageQuery(Class<DTO> dtoClass, Object[] expressions, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), dtoClass, expressions);
    }

    public <T> DtoQuery<DTO, T> from(Class<T> entityClass) {
        String existingAlias = hasSubQuery() ? com.javaorm.core.proxy.RecordingContext.getAlias(entityClass) : null;
        DtoQuery<DTO, T> query = new DtoQuery<>(env, dtoClass, entityClass, existingAlias);
        if (expressions != null && expressions.length > 0) {
            query.select(expressions);
        }
        return query;
    }

    public <T> DtoQuery<DTO, T> from(Cte<T> cte) {
        DtoQuery<DTO, T> query = new DtoQuery<>(env, dtoClass, cte);
        if (expressions != null && expressions.length > 0) {
            query.select(expressions);
        }
        return query;
    }

    public SqlEnv getEnv() {
        return env;
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
