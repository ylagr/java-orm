package com.javaorm.core.ast;

/**
 * SQL 表达式接口（列引用、SQL 函数、子查询、原生 SQL 片段等）。
 */
public interface SqlExpression {
    String getAlias();
    SqlExpression as(String alias);
}
