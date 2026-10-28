package com.javaorm.core.ast;

public class SubQueryExpression implements SqlExpression {

    private final Object query; // Query<?>
    private String alias;

    public SubQueryExpression(Object query) {
        this.query = query;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public SubQueryExpression as(String alias) {
        this.alias = alias;
        return this;
    }

    public Object getQuery() {
        return query;
    }
}
