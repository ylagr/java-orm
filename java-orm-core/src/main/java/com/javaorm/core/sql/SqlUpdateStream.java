package com.javaorm.core.sql;

import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.mutation.UpdateBuilder;
import com.javaorm.core.query.WhereBuilder;

import java.util.function.Consumer;

/**
 * UPDATE 更新流式管道构建器。
 * 支持完整 SQL 语序：UPDATE -> SET -> WHERE -> .exec()。
 */
public class SqlUpdateStream {

    private final SqlEnv env;
    private final SqlTableInfo tableInfo;
    @SuppressWarnings("rawtypes")
    private final UpdateBuilder updateBuilder;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SqlUpdateStream(SqlEnv env, SqlTableInfo tableInfo) {
        this.env = env;
        this.tableInfo = tableInfo;
        this.updateBuilder = new UpdateBuilder(tableInfo.getEntityClass(), env.getDataSource(), env.getDialect(), env.getConfig());
    }

    public SqlTableInfo getTableInfo() {
        return tableInfo;
    }

    @SuppressWarnings("unchecked")
    public SqlUpdateStream set(Object property, Object value) {
        updateBuilder.set(property, value);
        return this;
    }

    public SqlUpdateStream set(boolean condition, Object property, Object value) {
        if (condition) {
            set(property, value);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> WhereBuilder<T> where() {
        return (WhereBuilder<T>) updateBuilder.where();
    }

    public SqlUpdateStream where(Consumer<WhereBuilder<?>> consumer) {
        if (consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    public int execute() {
        return updateBuilder.execute();
    }

    public int exec() {
        return execute();
    }

    public String toSql() {
        return updateBuilder.toSql();
    }

    public SqlAndParams toSqlAndParams() {
        return updateBuilder.toSqlAndParams();
    }

    public SqlUpdateStream printSql(Boolean print) {
        if (print == null || print) {
            updateBuilder.printSql();
        } else {
            updateBuilder.printSql(false);
        }
        return this;
    }
}
