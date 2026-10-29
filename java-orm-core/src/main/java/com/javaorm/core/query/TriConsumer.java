package com.javaorm.core.query;

/**
 * 支持三个参数的函数式接口。
 */
@FunctionalInterface
public interface TriConsumer<T, U, V> {
    void accept(T t, U u, V v);
}
