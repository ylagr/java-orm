package com.javaorm.core.dialect;

import java.util.Collections;
import java.util.List;

public class SqlAndParams {
    private final String sql;
    private final List<Object> params;

    public SqlAndParams(String sql, List<Object> params) {
        this.sql = sql;
        this.params = params != null ? params : Collections.emptyList();
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "SQL: " + sql + "\nParams: " + params;
    }
}
