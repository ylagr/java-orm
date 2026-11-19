package com.javaorm.core.annotation;

import java.lang.annotation.*;

/**
 * 多索引声明容器注解（支持在类级别定义多个索引）
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JoIndexes {
    JoIndex[] value();
}
