package com.javaorm.core.operator;

import com.javaorm.core.proxy.PropertyPath;

/**
 * 带有 PropertyPath 语义的条件追加函数接口。
 */
@FunctionalInterface
public interface PathConditionAppender<TQuery> {
    void append(PropertyPath path, SqlOperator operator, Object... values);
}
