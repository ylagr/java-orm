package com.javaorm.core.fastbean;

/**
 * 实体/DTO 高性能实例构造函数接口（由 LambdaMetafactory 动态生成，避免反射 newInstance）。
 *
 * @param <T> 实体类型
 */
@FunctionalInterface
public interface FastBeanCreator<T> {

    /**
     * 创建目标对象新实例
     */
    T create();
}
