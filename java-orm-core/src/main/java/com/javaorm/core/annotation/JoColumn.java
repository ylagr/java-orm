package com.javaorm.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体属性对应的列名（可选）。
 * 若省略，则按全局 NameConversion 规则将字段驼峰命名转为下划线列名。
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JoColumn {
    /**
     * 列名别名（支持 @JoColumn("user_name") 简写）
     */
    String value() default "";

    /**
     * 数据库列名，为空则按驼峰转下划线规则推导
     */
    String name() default "";

    /**
     * 是否为主键（也可使用 @JoId 注解）
     */
    boolean primaryKey() default false;

    /**
     * 是否可为空
     */
    boolean nullable() default true;

    /**
     * 字段长度（如字符串 VARCHAR 长度，默认 255）
     */
    int length() default 255;

    /**
     * 浮点/定点数精度（整数部分+小数部分总位数，默认 0）
     */
    int precision() default 0;

    /**
     * 定点数小数位精度（默认 0）
     */
    int scale() default 0;

    /**
     * 显式指定数据库列类型 DDL 定义（例如 "TEXT"、"VARCHAR(100) NOT NULL"），若非空则优先使用
     */
    String columnDefinition() default "";

    /**
     * 字段注释
     */
    String comment() default "";

    /**
     * 默认值表达式（如 "0"、"''"）
     */
    String defaultValue() default "";

    /**
     * 是否忽略该字段（不参与 SQL 增删改查）
     */
    boolean ignore() default false;
}
