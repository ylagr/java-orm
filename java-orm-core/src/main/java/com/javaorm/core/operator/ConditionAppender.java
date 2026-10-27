package com.javaorm.core.operator;

import com.javaorm.core.proxy.PropertyPath;

@FunctionalInterface
public interface ConditionAppender<TQuery> {
    void append(PropertyPath path, SqlOperator operator, Object... values);
}
