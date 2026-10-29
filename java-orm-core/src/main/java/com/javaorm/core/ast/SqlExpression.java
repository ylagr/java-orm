package com.javaorm.core.ast;

/**
 * SQL 表达式接口（列引用、SQL 函数、子查询、原生 SQL 片段等）。
 *
 * @param <T> 表达式求值结果的 Java 映射强类型
 */
public interface SqlExpression<T> {

    String getAlias();

    SqlExpression<T> as(String alias);

    default WindowFunctionExpression<T> over() {
        return new WindowFunctionExpression<>(this);
    }
}
