package com.javaorm.core.operator;

/**
 * 框架默认内置的常用 SQL 操作符枚举。
 */
public enum BuiltinSqlOperator implements SqlOperator {
    EQ("="),
    NE("<>"),
    GT(">"),
    GE(">="),
    LT("<"),
    LE("<="),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),
    IN("IN"),
    NOT_IN("NOT IN"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL"),
    BETWEEN("BETWEEN");

    private final String sql;

    BuiltinSqlOperator(String sql) {
        this.sql = sql;
    }

    @Override
    public String getSql() {
        return sql;
    }

    @Override
    public String getName() {
        return name();
    }
}
