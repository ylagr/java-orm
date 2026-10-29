package com.javaorm.core.operator;

import com.javaorm.core.dialect.Dialect;

/**
 * 方言自定义操作符渲染函数式接口。
 */
@FunctionalInterface
public interface OperatorRenderer {

    String render(SqlOperator operator, Dialect dialect);
}
