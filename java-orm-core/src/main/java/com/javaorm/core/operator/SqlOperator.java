package com.javaorm.core.operator;

public enum SqlOperator {
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

    SqlOperator(String sql) {
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }
}
