package com.javaorm.core.sql;

import com.javaorm.core.mutation.UpdateBuilder;

/**
 * UPDATE 更新流式管道构建器。
 * 直接继承成熟的 UpdateBuilder，遵循 KISS 原则，消除冗余字段与重复委托逻辑。
 *
 * @param <T> 实体类型
 */
public class SqlUpdateStream<T> extends UpdateBuilder<T> {

    private final SqlTableInfo<T> tableInfo;

    public SqlUpdateStream(SqlEnv env, SqlTableInfo<T> tableInfo) {
        super(env, tableInfo != null ? tableInfo.getEntityClass() : null);
        this.tableInfo = tableInfo;
    }

    public SqlTableInfo<T> getTableInfo() {
        return tableInfo;
    }

    @Override
    public SqlUpdateStream<T> set(Object propVal, Object value) {
        super.set(propVal, value);
        return this;
    }

    @Override
    public SqlUpdateStream<T> set(boolean condition, Object propVal, Object value) {
        super.set(condition, propVal, value);
        return this;
    }
}
