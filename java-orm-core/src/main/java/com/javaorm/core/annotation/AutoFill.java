package com.javaorm.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段自动填充注解（例如 create_time, update_time, creator 等）。
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    AutoFillType value() default AutoFillType.INSERT;
}
