package com.javaorm.core.operator;

import java.util.Objects;

/**
 * 用户自定义或动态扩展的简单 SqlOperator 实现。
 */
public class SimpleSqlOperator implements SqlOperator {

    private final String sql;

    public SimpleSqlOperator(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("Operator SQL cannot be null or empty");
        }
        this.sql = sql.trim();
    }

    @Override
    public String getSql() {
        return sql;
    }

    @Override
    public String toString() {
        return sql;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SqlOperator)) return false;
        SqlOperator that = (SqlOperator) o;
        return Objects.equals(sql.toUpperCase(), that.getSql().toUpperCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(sql.toUpperCase());
    }
}
