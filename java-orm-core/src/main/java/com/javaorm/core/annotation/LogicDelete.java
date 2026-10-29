package com.javaorm.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 逻辑删除注解。
 * @deprecated 请使用 {@link JoLogicDelete}
 */
@Deprecated
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogicDelete {
    /**
     * 未删除时的正常值（默认 "0"）
     */
    String normalValue() default "0";

    /**
     * 已删除时的值（默认 "1"）
     */
    String deletedValue() default "1";
}
