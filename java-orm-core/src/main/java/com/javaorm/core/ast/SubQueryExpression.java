package com.javaorm.core.ast;

public class SubQueryExpression<T> implements SqlExpression<T> {

    private final Object query;
    private String alias;

    public SubQueryExpression(Object query) {
        this.query = query;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public SubQueryExpression<T> as(String alias) {
        this.alias = alias;
        return this;
    }

    public Object getQuery() {
        return query;
    }
}
