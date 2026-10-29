package com.javaorm.core.function;

import com.javaorm.core.dialect.Dialect;

import java.util.List;

/**
 * SQL 函数方言渲染器接口。
 * 负责在具体数据库方言下将逻辑函数及其参数编译为目标 SQL 片段及 PreparedStatement 参数。
 */
@FunctionalInterface
public interface FunctionRenderer {

    /**
     * 渲染指定函数调用。
     *
     * @param function 函数逻辑标识符
     * @param args     传入的参数列表（支持 ColumnExpression, SqlExpression, 纯常量值等）
     * @param sql      目标 SQL 缓冲区
     * @param params   SQL 参数列表
     * @param dialect  当前活跃的方言实例
     */
    void render(SqlFunction function, List<Object> args, StringBuilder sql, List<Object> params, Dialect dialect);
}
