package com.javaorm.core.sql;

import com.javaorm.core.mutation.DeleteBuilder;

/**
 * DELETE 删除流式管道构建器。
 * 直接继承成熟的 DeleteBuilder，遵循 KISS 原则，消除冗余字段与重复委托逻辑。
 *
 * @param <T> 实体类型
 */
public class SqlDeleteStream<T> extends DeleteBuilder<T> {

    private final SqlTableInfo<T> tableInfo;

    public SqlDeleteStream(SqlEnv env, SqlTableInfo<T> tableInfo) {
        super(env, tableInfo != null ? tableInfo.getEntityClass() : null);
        this.tableInfo = tableInfo;
    }

    public SqlTableInfo<T> getTableInfo() {
        return tableInfo;
    }
}
