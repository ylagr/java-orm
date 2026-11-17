package com.javaorm.core.annotation;

import com.javaorm.core.converter.ValueConverter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段值转换器注解。
 * 作用于实体属性或 Getter 方法上，显式绑定特定的 {@link ValueConverter} 实现。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoConverter {

    /**
     * 指定自定义的值转换器实现类。
     */
    Class<? extends ValueConverter<?, ?>> value();
}
