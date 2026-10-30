package com.javaorm.core.fastbean;

/**
 * 实体/DTO 属性高性能设置接口（由 LambdaMetafactory 动态绑定 Setter 方法直接调用，零反射）。
 */
@FunctionalInterface
public interface FastPropertySetter {

    /**
     * 为目标对象的属性赋值
     *
     * @param target 目标实体/DTO 对象
     * @param value  属性值
     */
    void set(Object target, Object value);
}
