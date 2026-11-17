package com.javaorm.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记枚举类中用于数据库物理存储的属性字段或 Getter 方法。
 * 零反射、零侵入，框架启动期会自动为该枚举生成 Lambda 直接调用器与 O(1) 紧凑查找表。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoEnumValue {
}
