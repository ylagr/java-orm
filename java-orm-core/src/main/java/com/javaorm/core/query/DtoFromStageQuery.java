package com.javaorm.core.query;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;

import javax.sql.DataSource;

/**
 * 带有 DTO 类和投影字段的第一阶段（等待 .from(Entity.class) 绑定数据源实体）。
 */
public class DtoFromStageQuery<DTO> {

    private final Class<DTO> dtoClass;
    private final Object[] expressions;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public DtoFromStageQuery(Class<DTO> dtoClass, Object[] expressions, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.dtoClass = dtoClass;
        this.expressions = expressions;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
    }

    public <T> DtoQuery<DTO, T> from(Class<T> entityClass) {
        String existingAlias = hasSubQuery() ? com.javaorm.core.proxy.RecordingContext.getAlias(entityClass) : null;
        DtoQuery<DTO, T> query = new DtoQuery<>(dtoClass, entityClass, dataSource, dialect, config, existingAlias);
        if (expressions != null && expressions.length > 0) {
            query.select(expressions);
        }
        return query;
    }

    public <T> DtoQuery<DTO, T> from(Cte<T> cte) {
        DtoQuery<DTO, T> query = new DtoQuery<>(dtoClass, cte, dataSource, dialect, config);
        if (expressions != null && expressions.length > 0) {
            query.select(expressions);
        }
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
