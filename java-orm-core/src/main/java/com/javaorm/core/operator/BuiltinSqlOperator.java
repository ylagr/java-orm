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
    BETWEEN("BETWEEN"),
    IS_EMPTY("IS EMPTY"),
    IS_NOT_EMPTY("IS NOT EMPTY"),
    IS_BLANK("IS BLANK"),
    IS_NOT_BLANK("IS NOT BLANK"),
    RANGE_CLOSED("RANGE_CLOSED"),
    RANGE_OPEN("RANGE_OPEN"),
    RANGE_CLOSED_OPEN("RANGE_CLOSED_OPEN"),
    RANGE_OPEN_CLOSED("RANGE_OPEN_CLOSED");

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
