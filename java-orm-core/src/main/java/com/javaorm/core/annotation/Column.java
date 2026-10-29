package com.javaorm.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体属性对应的列名（可选）。
 * @deprecated 请使用 {@link JoColumn}
 */
@Deprecated
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    /**
     * 数据库列名，为空则按驼峰转下划线规则推导
     */
    String name() default "";

    /**
     * 是否为主键（也可使用 @Id 注解）
     */
    boolean primaryKey() default false;

    /**
     * 是否可为空
     */
    boolean nullable() default true;

    /**
     * 是否忽略该字段（不参与 SQL 增删改查）
     */
    boolean ignore() default false;
}
