package com.javaorm.core.fastbean;

/**
 * 实体/DTO 属性高性能读取接口（由 LambdaMetafactory 动态绑定 Getter 方法直接调用，零反射）。
 */
@FunctionalInterface
public interface FastPropertyGetter {

    /**
     * 获取目标对象的属性值
     *
     * @param target 目标实体/DTO 对象
     * @return 属性值
     */
    Object get(Object target);
}
