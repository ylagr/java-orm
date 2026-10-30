package com.javaorm.core.sql;

import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.mutation.DeleteBuilder;
import com.javaorm.core.query.WhereBuilder;

import java.util.function.Consumer;

/**
 * DELETE 删除流式管道构建器。
 * 支持完整 SQL 语序：DELETE -> WHERE -> .exec()。
 */
public class SqlDeleteStream {

    private final SqlEnv env;
    private final SqlTableInfo tableInfo;
    @SuppressWarnings("rawtypes")
    private final DeleteBuilder deleteBuilder;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SqlDeleteStream(SqlEnv env, SqlTableInfo tableInfo) {
        this.env = env;
        this.tableInfo = tableInfo;
        this.deleteBuilder = new DeleteBuilder(tableInfo.getEntityClass(), env.getDataSource(), env.getDialect(), env.getConfig());
    }

    public SqlTableInfo getTableInfo() {
        return tableInfo;
    }

    @SuppressWarnings("unchecked")
    public <T> WhereBuilder<T> where() {
        return (WhereBuilder<T>) deleteBuilder.where();
    }

    public SqlDeleteStream where(Consumer<WhereBuilder<?>> consumer) {
        if (consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    public int execute() {
        return deleteBuilder.execute();
    }

    public int exec() {
        return execute();
    }

    public String toSql() {
        return deleteBuilder.toSql();
    }

    public SqlAndParams toSqlAndParams() {
        return deleteBuilder.toSqlAndParams();
    }

    public SqlDeleteStream printSql(Boolean print) {
        if (print == null || print) {
            deleteBuilder.printSql();
        } else {
            deleteBuilder.printSql(false);
        }
        return this;
    }
}
