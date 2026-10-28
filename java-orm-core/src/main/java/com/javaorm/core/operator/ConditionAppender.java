package com.javaorm.core.operator;

@FunctionalInterface
public interface ConditionAppender<TQuery> {
    void append(SqlOperator operator, Object... values);
}
