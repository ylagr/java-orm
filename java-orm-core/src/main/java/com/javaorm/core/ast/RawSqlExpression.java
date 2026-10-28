package com.javaorm.core.ast;

public class RawSqlExpression implements SqlExpression {

    private final String rawSql;
    private final Object[] params;
    private String alias;

    public RawSqlExpression(String rawSql, Object... params) {
        this.rawSql = rawSql;
        this.params = params != null ? params : new Object[0];
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public RawSqlExpression as(String alias) {
        this.alias = alias;
        return this;
    }

    public String getRawSql() { return rawSql; }
    public Object[] getParams() { return params; }
}
