package com.javaorm.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体对应的数据库表名（可选）。
 * 若省略，则默认按全局 NameConversion 规则将实体类名转为表名。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {
    /**
     * 表名，留空则根据类名自动推导
     */
    String name() default "";

    /**
     * 模式或数据库名（可选）
     */
    String schema() default "";
}
